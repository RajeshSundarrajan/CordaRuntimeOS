package net.corda.p2p.gateway.domino

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentLinkedDeque

abstract class LeafTile(coordinatorFactory: LifecycleCoordinatorFactory) : DominoTile(coordinatorFactory) {
    companion object {
        private val logger = contextLogger()
    }
    private val stopActions = ConcurrentLinkedDeque<()->Unit>()
    fun executeBeforeStop(action: () -> Unit) {
        stopActions.addFirst(action)
    }

    override fun startTile() {
        @Suppress("TooGenericExceptionCaught")
        try {
            createResources()
        } catch (e: Throwable) {
            gotError(e)
        }
    }

    abstract fun createResources()

    override fun stopTile() {
        stopActions.onEach {
            @Suppress("TooGenericExceptionCaught")
            try {
                it.invoke()
            } catch (e: Throwable) {
                logger.warn("Fail to stop", e)
            }
        }
        stopActions.clear()
    }
}