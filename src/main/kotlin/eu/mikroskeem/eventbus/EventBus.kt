/*
 * This file is part of project Eventbus, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 Mark Vainomaa <mikroskeem@mikroskeem.eu>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package eu.mikroskeem.eventbus

import eu.mikroskeem.eventbus.EventBus.Builder
import eu.mikroskeem.eventbus.annotations.Subscribe
import eu.mikroskeem.eventbus.executor.ExecutorFactory
import org.slf4j.LoggerFactory
import java.lang.reflect.Modifier.isPublic
import java.lang.reflect.Modifier.isStatic
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * An event bus
 *
 * @param eventInterface An event interface
 * @param listenerInterface A listener interface
 */
class EventBus<E: Any, L: Any> internal constructor(val eventInterface: Class<E>, val listenerInterface: Class<L>) {
    private val logger = LoggerFactory.getLogger(EventBus::class.java)
    private val listeners: MutableMap<Class<E>, MutableList<EventExecutor<E, L>>> = ConcurrentHashMap()
    private val factory: ExecutorFactory<E, L> = ExecutorFactory()

    /**
     * Registers a listener to this event bus instance
     *
     * @param listener Listener instance
     */
    fun registerListener(listener: L) {
        val listsToSort = HashSet<MutableList<EventExecutor<*, L>>>()

        // Find all appropriate event handler methods
        listener::class.java.methods
                .filter { it.returnType == Void.TYPE && it.parameterCount == 1 }
                .filter { it.modifiers.let { isPublic(it) && !isStatic(it) } && eventInterface.isAssignableFrom(it.parameterTypes[0]) }
                .forEach {
                    @Suppress("UNCHECKED_CAST")
                    val eventClass = it.parameterTypes[0] as Class<E>
                    val subscribeMethodInfo = it.getAnnotation(Subscribe::class.java) ?: return@forEach
                    val executorsList = listeners.computeIfAbsent(eventClass) { ArrayList() }

                    // Generate executor
                    val executor = factory.generateExecutor(listener, it, eventClass, subscribeMethodInfo)

                    // Add executor into set and sort the list
                    executorsList.add(executor)
                    @Suppress("UNCHECKED_CAST")
                    listsToSort.add(executorsList as MutableList<EventExecutor<*, L>>)
        }

        // Sort event handler lists
        listsToSort.forEach { it.sort() }
    }

    /**
     * Unregisters a listener from this event bus instance
     *
     * @param listener Listener instance
     */
    fun unregisterListener(listener: L) {
        listeners.values.forEach { executors ->
            ArrayList(executors).filter { it.owningClass == listener }.forEach {
                executors.remove(it)
            }
        }
    }

    /**
     * Calls an event on this event bus instance
     *
     * @param event An event to pass through all the listeners
     * @return Given event instance, for chaining
     */
    fun callEvent(event: E): E = event.also {
        listeners[event::class.java]?.forEach {
            try {
                it.fire(event)
            } catch (t: Throwable) {
                // Log the exception
                if(logger.isWarnEnabled)
                    logger.warn("Failed to pass event ${event::class.className} to " +
                            "${it.owningClass.className}::${it.targetMethod.name}", t)
            }
        }
    }

    /**
     * Event bus builder
     */
    class Builder<E: Any, L: Any> {
        private lateinit var eventInterface: Class<E>
        private lateinit var listenerInterface: Class<L>

        /**
         * Sets event interface type
         *
         * @param eventInterface Event interface
         * @return This builder instance, for chaining
         */
        fun setEventInterface(eventInterface: KClass<E>): Builder<E, L> = this.also { it ->
            it.eventInterface = eventInterface.java
        }

        /**
         * Sets listener interface type
         *
         * @param listenerInterface Listener interface
         * @return This builder instance, for chaining
         */
        fun setListenerInterface(listenerInterface: KClass<L>): Builder<E, L> = this.also { it ->
            it.listenerInterface = listenerInterface.java
        }

        /**
         * Builds an event bus
         *
         * @return Instance of event bus
         */
        fun build(): EventBus<E, L> {
            if(!::eventInterface.isInitialized)
                throw IllegalStateException("Event interface type is not set!")

            if(!::listenerInterface.isInitialized)
                throw IllegalStateException("Listener interface type is not set!")

            if(!eventInterface.isInterface)
                throw IllegalStateException("Event interface is not an interface!")

            if(!listenerInterface.isInterface)
                throw IllegalStateException("Listener interface is not an interface!")

            return EventBus(eventInterface, listenerInterface)
        }
    }
}

/**
 * Shortcut method to build event bus quickly. Event and listener interface types are inferred from
 * field type parameters.
 */
inline fun <reified E: Any, reified L: Any> newEventBus(): EventBus<E, L> {
    return Builder<E, L>()
            .setEventInterface(E::class)
            .setListenerInterface(L::class)
            .build()
}

private val Class<*>.className: String get() = this.name.run { substring(lastIndexOf(".")) }
private val KClass<*>.className: String get() = this.java.className