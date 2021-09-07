package net.corda.osgi.api

/**
 * The `osgi-framework-bootstrap` module calls [run] passing the process cli arguments as parameters
 * and shutdowns the framework cleanly when the method terminates using the value returned by the [run] method
 * as the process exit codes. If a framework contains are multiple OSGi Components implementing
 * the [Application] service an error is raised
 *
 * **NOTE:**
 * *To distribute an application as a bootable JAR built with the `corda.common.app` plugin,
 * only one class must implement this interface because that class is the entry point of the application.*
 *
 * The class implementing this interface must define an OSGi component and register as an OSGi service.
 *
 * **EXAMPLE**
 *
 * The code shows for to implement the Application interface and inject in the constructor a OSGi service/component
 * annotated the `@Reference`.
 *
 * See the `README.md` file of the `buildSrc` module for the `common-app`plugin for more infos.
 *
 * ```kotlin
 * import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
 * import net.corda.osgi.api.Application
 * import org.osgi.service.component.annotations.Activate
 * import org.osgi.service.component.annotations.Component
 * import org.osgi.service.component.annotations.Reference
 *
 * @Component(immediate = true)
 * class App @Activate constructor(
 * @Reference(service = KafkaTopicAdmin::class)
 * private var kafkaTopicAdmin: KafkaTopicAdmin,
 * ): Application {
 *
 *  override fun run(args: Array<String>) : Int {
 *      println("startup with ${kafkaTopicAdmin}")
 *  }
 *
 *}
 * ```
 */
interface Application {

    /**
     * The `osgi-framework-bootstrap` module calls this method as entry point of the application.
     *
     * @param args passed from the OS starting the bootable JAR.
     */
    fun run(args: Array<String>) : Int
}