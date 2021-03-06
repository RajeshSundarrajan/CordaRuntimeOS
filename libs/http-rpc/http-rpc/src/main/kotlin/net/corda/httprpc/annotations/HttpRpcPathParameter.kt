package net.corda.httprpc.annotations

/**
 * Marks a function parameter of a function annotated with @[HttpRpcGET] or @[HttpRpcPOST] as a path parameter.

 * Path parameters need to also be defined in the endpoint's path, in the form of "/:parameter/".
 *
 * @property name The name of the path parameter within the endpoint's path. Should be specified in lower case.
 *      Defaults to the parameter's name in the function signature.
 * @property description The description of the path parameter, used for documentation. Defaults to empty string.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class HttpRpcPathParameter(
    val name: String = "",
    val description: String = ""
)