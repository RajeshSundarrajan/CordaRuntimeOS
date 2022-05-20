package net.corda.membership.impl.registration.dynamic.processing.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.registration.DeclineRegistration
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.StartRegistration
import net.corda.data.membership.command.registration.VerifyMember
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.membership.MemberInfoFactory
import net.corda.membership.impl.registration.dynamic.RegistrationRequest
import net.corda.membership.impl.registration.dynamic.RegistrationRequestImpl
import net.corda.membership.impl.toSortedMap
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.CREATION_TIME
import net.corda.v5.membership.MEMBER_STATUS_PENDING
import net.corda.v5.membership.MODIFIED_TIME
import net.corda.v5.membership.MemberInfo
import net.corda.v5.membership.STATUS
import net.corda.v5.membership.toAvro
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import java.time.Duration
import java.time.Instant
import java.util.*

class StartRegistrationHandler(
    private val clock: Clock,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    private val memberInfoFactory: MemberInfoFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val databaseSender: RPCSender<MembershipPersistenceRequest, MembershipPersistenceResponse>
) : RegistrationHandler {

    private companion object {
        const val RPC_TIMEOUT_MS = 10000L

        val logger = contextLogger()
    }

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer({
            logger.error("Deserialization of registration request KeyValuePairList failed.")
        }, KeyValuePairList::class.java)

    override fun invoke(command: Record<String, RegistrationCommand>): RegistrationHandlerResult {
        require(command.value!!.command is StartRegistration) {
            "Incorrect handler used for command of type ${command.value!!.command::class.java}"
        }
        val startRegistrationCommand = command.value!!.command as StartRegistration
        // 1) persist the received registration request before verifying it's contents
        persistRegistrationRequest(startRegistrationCommand)

        // 2) Validate the contents of the registration request
        val mgm = startRegistrationCommand.destination.toCorda()
        val registeringMember = startRegistrationCommand.source.toCorda()

        val regRq: RegistrationRequest = layeredPropertyMapFactory.create<RegistrationRequestImpl>(
            keyValuePairListDeserializer.deserialize(
                startRegistrationCommand.memberRegistrationRequest.memberContext.array()
            )?.toSortedMap() ?: emptyMap()
        )
        val pendingMemberInfo = buildPendingMemberInfo(regRq)

        val outputCommand = RegistrationCommand(
            try {
                validateRegistrationRequest(regRq.entries.isNotEmpty()) {
                    "Empty member context in the registration request."
                }
                val mgmMemberName = MemberX500Name.parse(mgm.x500Name)
                val mgmMemberInfo = membershipGroupReaderProvider.getGroupReader(mgm).lookup(mgmMemberName)
                // The destination is an MGM
                validateRegistrationRequest(mgmMemberInfo != null) {
                    "Could not find MGM matching name: [$mgmMemberName]"
                }
                validateRegistrationRequest(mgmMemberInfo!!.isMgm) {
                    "Registration request is targeted at non-MGM holding identity."
                }

                // 2.2) The signature over the member context is valid - To do

                // Parse the registration request and verify contents
                // The MemberX500Name matches the source MemberX500Name from the P2P messaging
                validateRegistrationRequest(pendingMemberInfo.name == MemberX500Name.parse(registeringMember.x500Name)) {
                    "MemberX500Name in registration request does not match member sending request over P2P."
                }

                // The MemberX500Name is not a duplicate
                validateRegistrationRequest(queryMemberInfo(mgm, registeringMember) == null) {
                    "Member Info already exists for applying member"
                }

                // The group ID matches the group ID of the MGM
                validateRegistrationRequest(pendingMemberInfo.groupId == mgmMemberInfo.groupId) {
                    "Group ID in registration request does not match the group ID of the target MGM."
                }

                // There is at least one endpoint specified
                validateRegistrationRequest(pendingMemberInfo.endpoints.isNotEmpty()) {
                    "Registering member has not specified any endpoints"
                }

                // Request is valid and can move on to verification
                VerifyMember()
            } catch (ex: InvalidRegistrationRequestException) {
                DeclineRegistration(ex.originalMessage)
            } catch (ex: Exception) {
                DeclineRegistration("Failed to verify registration request due to: [${ex.message}]")
            }
        )

        // 2.4) Persist pending member info
        persistMemberInfo(mgm, pendingMemberInfo)

        // 2.5) Create registration state with member ID and registration ID
        val state = RegistrationState(
            regRq.registrationId,
            startRegistrationCommand.source
        )

        return RegistrationHandlerResult(
            state,
            listOf(
                Record(
                    command.topic,
                    command.key,
                    outputCommand
                )
            )
        )
    }

    private class InvalidRegistrationRequestException(reason: String) : CordaRuntimeException(reason)

    private fun validateRegistrationRequest(condition: Boolean, errorMsg: () -> String) {
        if (!condition) {
            throw InvalidRegistrationRequestException(errorMsg.invoke())
        }
    }

    private fun buildPendingMemberInfo(registrationRequest: RegistrationRequest): MemberInfo {
        val now = clock.instant().toString()
        return memberInfoFactory.create(
            registrationRequest.entries.associate { it.key to it.value }.toSortedMap(),
            sortedMapOf(
                CREATION_TIME to now,
                MODIFIED_TIME to now,
                STATUS to MEMBER_STATUS_PENDING
            )

        )
    }

    private fun persistRegistrationRequest(registrationStartCommand: StartRegistration) {
        logger.info("Persisting the member registration request.")
        MembershipPersistenceRequest(
            buildMembershipRequestContext(registrationStartCommand.destination.toCorda().id),
            PersistRegistrationRequest(
                RegistrationStatus.NEW,
                registrationStartCommand.memberRegistrationRequest
            )
        ).execute()
    }

    private fun persistMemberInfo(mgm: HoldingIdentity, memberInfo: MemberInfo) {
        logger.info("Persisting the member info request.")
        val avroMemberInfo = memberInfo.toAvro()
        MembershipPersistenceRequest(
            buildMembershipRequestContext(mgm.id),
            PersistMemberInfo(
                listOf(
                    PersistentMemberInfo(mgm.toAvro(), avroMemberInfo.memberContext, avroMemberInfo.mgmContext)
                )
            )
        ).execute()
    }

    private fun buildMembershipRequestContext(holdingIdentityId: String) = MembershipRequestContext(
        clock.instant(),
        UUID.randomUUID().toString(),
        holdingIdentityId
    )

    private fun MembershipPersistenceRequest.execute(): MembershipPersistenceResponse {
        val response = databaseSender
            .sendRequest(this)
            .getOrThrow(Duration.ofMillis(RPC_TIMEOUT_MS))

        with(context) {
            require(holdingIdentityId == response.context.holdingIdentityId) {
                "Holding identity in the response received does not match what was sent in the request."
            }
            require(requestTimestamp == response.context.requestTimestamp) {
                "Request timestamp in the response received does not match what was sent in the request."
            }
            require(requestId == response.context.requestId) {
                "Holding identity in the response received does not match what was sent in the request."
            }
            require(requestTimestamp <= response.context.responseTimestamp) {
                "Response timestamp is before the request timestamp"
            }
        }
        return response
    }

    private fun queryMemberInfo(mgm: HoldingIdentity, member: HoldingIdentity): MemberInfo? {
        MembershipPersistenceRequest(
            buildMembershipRequestContext(mgm.id),
            QueryMemberInfo(listOf(member.toAvro()))
        ).execute()
        return null
//        if(response.success) {
//            null //response.payload.members.singleOrNull() - need converter
//        } else {
//            null
//        }
    }
}