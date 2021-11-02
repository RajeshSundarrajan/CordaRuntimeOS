package net.corda.sandbox.internal

import net.corda.packaging.CPK
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.ClassTagV1.EVOLVABLE_IDENTIFIER
import net.corda.sandbox.internal.ClassTagV1.PLACEHOLDER_HASH
import net.corda.sandbox.internal.ClassTagV1.PLACEHOLDER_STRING
import net.corda.sandbox.internal.ClassTagV1.STATIC_IDENTIFIER
import net.corda.sandbox.internal.classtag.ClassTag
import net.corda.sandbox.internal.classtag.ClassTagFactory
import net.corda.sandbox.internal.classtag.ClassType
import net.corda.sandbox.internal.classtag.EvolvableTag
import net.corda.sandbox.internal.classtag.StaticTag
import net.corda.sandbox.internal.sandbox.CpkSandbox
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import net.corda.sandbox.internal.sandbox.SandboxImpl
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import java.util.UUID.randomUUID

// Various dummy serialised class tags.
private const val CPK_STATIC_TAG = "serialised_static_cpk_class"
private const val PUBLIC_STATIC_TAG = "serialised_static_public_class"
private const val BAD_CPK_FILE_HASH_STATIC_TAG = "serialised_static_bad_cpk_file_hash"
private const val CPK_EVOLVABLE_TAG = "serialised_evolvable_cpk_class"
private const val PUBLIC_EVOLVABLE_TAG = "serialised_evolvable_public_class"
private const val BAD_MAIN_BUNDLE_NAME_EVOLVABLE_TAG = "serialised_evolvable_bad_main_bundle_name"
private const val BAD_SIGNERS_EVOLVABLE_TAG = "serialised_evolvable_bad_signers"

/**
 * Tests of [SandboxGroupImpl].
 *
 * There are no tests of the sandbox-retrieval and class-loading functionality, since this is likely to be deprecated.
 */
class SandboxGroupImplTests {
    private val nonBundleClass = Boolean::class.java
    private val cpkClass = String::class.java
    private val publicClass = Int::class.java
    private val nonSandboxClass = Float::class.java

    private val mockCpkBundle = mockBundle(CPK_BUNDLE_NAME, cpkClass)
    private val mockPublicBundle = mockBundle(PUBLIC_BUNDLE_NAME, publicClass)
    private val mockNonSandboxBundle = mockBundle()
    private val mockMainBundle = mockBundle(MAIN_BUNDLE_NAME)

    private val mockBundleUtils = mock<BundleUtils>().apply {
        whenever(getBundle(cpkClass)).thenReturn(mockCpkBundle)
        whenever(getBundle(publicClass)).thenReturn(mockPublicBundle)
        whenever(getBundle(nonSandboxClass)).thenReturn(mockNonSandboxBundle)
    }

    private val cpkSandbox =
        CpkSandboxImpl(mockBundleUtils, randomUUID(), mockCpk(), mockMainBundle, setOf(mockCpkBundle))
    private val publicSandbox = SandboxImpl(mockBundleUtils, randomUUID(), setOf(mockPublicBundle), emptySet())

    private val sandboxGroupImpl = SandboxGroupImpl(
        setOf(cpkSandbox), setOf(publicSandbox), DummyClassTagFactory(cpkSandbox.cpk), mockBundleUtils
    )

    @Test
    fun `creates valid static tag for a non-bundle class`() {
        val expectedTag = "$STATIC_IDENTIFIER;${null};${null}"
        assertEquals(expectedTag, sandboxGroupImpl.getStaticTag(nonBundleClass))
    }

    @Test
    fun `creates valid static tag for a CPK class`() {
        val expectedTag = "$STATIC_IDENTIFIER;$mockCpkBundle;$cpkSandbox"
        assertEquals(expectedTag, sandboxGroupImpl.getStaticTag(cpkClass))
    }

    @Test
    fun `creates valid static tag for a public class`() {
        val expectedTag = "$STATIC_IDENTIFIER;$mockPublicBundle;${null}"
        assertEquals(expectedTag, sandboxGroupImpl.getStaticTag(publicClass))
    }

    @Test
    fun `returns null if asked to create static tag for a class in a bundle not in the sandbox group`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getStaticTag(nonSandboxClass)
        }
    }

    @Test
    fun `creates valid evolvable tag for a non-bundle class`() {
        val expectedTag = "$EVOLVABLE_IDENTIFIER;${null};${null}"
        assertEquals(expectedTag, sandboxGroupImpl.getEvolvableTag(nonBundleClass))
    }

    @Test
    fun `creates valid evolvable tag for a CPK class`() {
        val expectedTag = "$EVOLVABLE_IDENTIFIER;$mockCpkBundle;$cpkSandbox"
        assertEquals(expectedTag, sandboxGroupImpl.getEvolvableTag(cpkClass))
    }

    @Test
    fun `creates valid evolvable tag for a public class`() {
        val expectedTag = "$EVOLVABLE_IDENTIFIER;$mockPublicBundle;${null}"
        assertEquals(expectedTag, sandboxGroupImpl.getEvolvableTag(publicClass))
    }

    @Test
    fun `throws if asked to create evolvable tag for a class in a bundle not in the sandbox group`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getEvolvableTag(nonSandboxClass)
        }
    }

    @Test
    fun `returns CPK class identified by a static tag`() {
        assertEquals(cpkClass, sandboxGroupImpl.getClass(cpkClass.name, CPK_STATIC_TAG))
    }

    @Test
    fun `returns public class identified by a static tag`() {
        assertEquals(publicClass, sandboxGroupImpl.getClass(publicClass.name, PUBLIC_STATIC_TAG))
    }

    @Test
    fun `returns CPK class identified by an evolvable tag`() {
        assertEquals(cpkClass, sandboxGroupImpl.getClass(cpkClass.name, CPK_EVOLVABLE_TAG))
    }

    @Test
    fun `returns public class identified by an evolvable tag`() {
        assertEquals(publicClass, sandboxGroupImpl.getClass(publicClass.name, PUBLIC_EVOLVABLE_TAG))
    }

    @Test
    fun `throws if asked to return class but cannot find matching sandbox for a static tag`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(cpkClass.name, BAD_CPK_FILE_HASH_STATIC_TAG)
        }
    }

    @Test
    fun `throws if asked to return class but cannot find matching sandbox for an evolvable tag`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(cpkClass.name, BAD_MAIN_BUNDLE_NAME_EVOLVABLE_TAG)
        }
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(cpkClass.name, BAD_SIGNERS_EVOLVABLE_TAG)
        }
    }

    @Test
    fun `throws if asked to return class but cannot find class in matching sandbox`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(nonSandboxClass.name, CPK_STATIC_TAG)
        }
    }
}

/** A dummy [StaticTag] implementation. */
private class StaticTagImpl(
    override val classType: ClassType,
    override val classBundleName: String,
    override val cpkFileHash: SecureHash
) : StaticTag() {
    override val version = 1
    override fun serialise() = ""
}

/** A dummy [EvolvableTag] implementation. */
private class EvolvableTagImpl(
    override val classType: ClassType,
    override val classBundleName: String,
    override val mainBundleName: String,
    override val cpkSignerSummaryHash: SecureHash?
) : EvolvableTag() {
    override val version = 1
    override fun serialise() = ""
}

/** A dummy [ClassTagFactory] implementation that returns pre-defined tags. */
private class DummyClassTagFactory(cpk: CPK) : ClassTagFactory {
    // Used for public classes, where the main bundle name, CPK file hash and CPK signer summary hash are ignored.
    val staticIdentifier = STATIC_IDENTIFIER
    val evolvableIdentifier = EVOLVABLE_IDENTIFIER

    private val cpkStaticTag = StaticTagImpl(ClassType.CpkSandboxClass, CPK_BUNDLE_NAME, cpk.metadata.hash)

    private val publicStaticTag = StaticTagImpl(ClassType.PublicSandboxClass, PUBLIC_BUNDLE_NAME, PLACEHOLDER_HASH)

    private val invalidCpkFileHashStaticTag =
        StaticTagImpl(ClassType.CpkSandboxClass, CPK_BUNDLE_NAME, randomSecureHash())

    private val cpkEvolvableTag =
        EvolvableTagImpl(
            ClassType.CpkSandboxClass,
            CPK_BUNDLE_NAME,
            MAIN_BUNDLE_NAME,
            cpk.metadata.id.signerSummaryHash
        )

    private val publicEvolvableTag =
        EvolvableTagImpl(ClassType.PublicSandboxClass, PUBLIC_BUNDLE_NAME, PLACEHOLDER_STRING, PLACEHOLDER_HASH)

    private val invalidMainBundleNameEvolvableTag =
        EvolvableTagImpl(
            ClassType.CpkSandboxClass,
            CPK_BUNDLE_NAME,
            "invalid_main_bundle_name",
            cpk.metadata.id.signerSummaryHash
        )

    private val invalidSignersEvolvableTag =
        EvolvableTagImpl(ClassType.CpkSandboxClass, CPK_BUNDLE_NAME, MAIN_BUNDLE_NAME, randomSecureHash())

    override fun createSerialisedTag(
        isStaticTag: Boolean,
        bundle: Bundle?,
        cpkSandbox: CpkSandbox?
    ): String {
        val tagIdentifier = if (isStaticTag) staticIdentifier else evolvableIdentifier
        return "$tagIdentifier;$bundle;$cpkSandbox"
    }

    override fun deserialise(serialisedClassTag: String): ClassTag {
        return when (serialisedClassTag) {
            CPK_STATIC_TAG -> cpkStaticTag
            PUBLIC_STATIC_TAG -> publicStaticTag
            BAD_CPK_FILE_HASH_STATIC_TAG -> invalidCpkFileHashStaticTag
            CPK_EVOLVABLE_TAG -> cpkEvolvableTag
            PUBLIC_EVOLVABLE_TAG -> publicEvolvableTag
            BAD_MAIN_BUNDLE_NAME_EVOLVABLE_TAG -> invalidMainBundleNameEvolvableTag
            BAD_SIGNERS_EVOLVABLE_TAG -> invalidSignersEvolvableTag
            else -> throw IllegalArgumentException("Could not deserialise tag.")
        }
    }
}