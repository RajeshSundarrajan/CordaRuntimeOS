package net.corda.p2p.gateway.domino

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleException
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

abstract class DominoTile(
    coordinatorFactory: LifecycleCoordinatorFactory,
) : Lifecycle {
    companion object {
        private val logger = contextLogger()
        private val instanceIndex = AtomicInteger(0)
    }
    enum class State {
        Created,
        Started,
        StoppedDueToError,
        StoppedByParent
    }
    val name = LifecycleCoordinatorName(
        javaClass.simpleName,
        instanceIndex.incrementAndGet().toString()
    )

    override fun start() {
        logger.info("Starting $name")
        when (state) {
            State.Created -> {
                coordinator.start()
            }
            State.Started -> {
                // Do nothing
            }
            State.StoppedByParent -> {
                startTile()
            }
            State.StoppedDueToError -> {
                logger.info("Can not start $name, it was stopped due to an error")
            }
        }
    }

    override fun stop() {
        if (state != State.StoppedByParent) {
            stopTile()
            updateState(State.StoppedByParent)
        }
    }

    protected val coordinator = coordinatorFactory.createCoordinator(name, EventHandler())

    private val currentState = AtomicReference(State.Created)

    val state: State
        get() = currentState.get()

    override val isRunning: Boolean
        get() = state == State.Started

    protected fun updateState(newState: State) {
        val oldState = currentState.getAndSet(newState)
        if ((newState != State.Started) && (oldState == State.Started)) {
            coordinator.updateStatus(LifecycleStatus.DOWN)
        } else if ((oldState != State.Started) && (newState == State.Started)) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
        logger.info("State of $name is $newState")
    }

    private inner class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when (event) {
                is ErrorEvent -> {
                    gotError(event.cause)
                }
                is StartEvent -> {
                    when (state) {
                        State.Created -> {
                            startTile()
                        }
                        else -> logger.warn("Unexpected start event, my state is $state")
                    }
                }
                is StopEvent -> {
                    // Do nothing
                }
                is RegistrationStatusChangeEvent -> {
                    if (event.status == LifecycleStatus.UP) {
                        onChildStarted()
                    } else {
                        onChildStopped()
                    }
                }
                else -> {
                    logger.warn("Unexpected event $event")
                }
            }
        }
    }

    protected open fun gotError(cause: Throwable) {
        logger.warn("Got error in $name", cause)
        if (state != State.StoppedDueToError) {
            stopTile()
            updateState(State.StoppedDueToError)
        }
    }

    protected open fun onChildStopped() {
        // Do nothing
    }

    protected open fun onChildStarted() {
        // Do nothing
    }

    protected abstract fun startTile()
    protected abstract fun stopTile()

    override fun close() {
        stopTile()

        try {
            coordinator.close()
        } catch (e: LifecycleException) {
            // This try-catch should be removed once CORE-2786 is fixed
            logger.debug("Could not close coordinator", e)
        }
    }
}