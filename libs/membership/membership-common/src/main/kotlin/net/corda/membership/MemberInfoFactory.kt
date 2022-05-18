package net.corda.membership

import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import java.util.*

interface MemberInfoFactory {
    fun create(
        memberContext: SortedMap<String, String?>,
        mgmContext: SortedMap<String, String?>
    ): MemberInfo

    fun create(
        memberContext: MemberContext,
        mgmContext: MGMContext
    ): MemberInfo

    fun createFromAvro(
        memberInfo: net.corda.data.membership.PersistentMemberInfo
    ): MemberInfo
}