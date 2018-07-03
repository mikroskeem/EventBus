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

package eu.mikroskeem.test.eventbus

import eu.mikroskeem.eventbus.EventBus
import eu.mikroskeem.eventbus.newEventBus
import eu.mikroskeem.test.eventbus.events.SimpleEvent
import eu.mikroskeem.test.eventbus.interfaces.Event
import eu.mikroskeem.test.eventbus.interfaces.Listener
import eu.mikroskeem.test.eventbus.listeners.SimpleListener
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

inline fun assert(value: Boolean, message: () -> String) {
    if(!value) throw IllegalStateException(message())
}

object EventBusSpec: Spek({
    given("an event bus instance") {
        val eventBus: EventBus<Event, Listener> = newEventBus()
        val listener = SimpleListener()

        on("simple listener registration") {
            it("should register successfully") {
                eventBus.registerListener(listener)
            }
        }

        on("simple event fire after listener registration") {
            val event = SimpleEvent()

            it("should fire successfully") {
                eventBus.callEvent(event)
            }

            it("should've passed one listener") {
                assert(event.firedCount == 1) { "event.firedCount != 1" }
            }
        }

        on("simple listener unregistration") {
            it("should unregister successfully") {
                eventBus.unregisterListener(listener)
            }
        }

        on("simple event fire after listener unregistration") {
            val event = SimpleEvent()

            it("should fire successfully") {
                eventBus.callEvent(event)
            }

            it("should've passed without any listeners mutating it") {
                assert(event.firedCount == 1) { "event.firedCount == 1" }
            }
        }
    }
})