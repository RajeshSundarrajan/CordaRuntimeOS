package net.corda.libs.packaging.core.exception

/** Thrown if the format version is not known. */
class UnknownFormatVersionException(message: String, cause: Throwable? = null) : PackagingException(message, cause)