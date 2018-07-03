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

package eu.mikroskeem.eventbus.executor

import eu.mikroskeem.eventbus.EventBus
import eu.mikroskeem.eventbus.EventExecutor
import eu.mikroskeem.eventbus.annotations.Priority
import eu.mikroskeem.eventbus.annotations.Subscribe
import org.objectweb.asm.Type
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.CHECKCAST
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.INVOKEINTERFACE
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.PUTFIELD
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Opcodes.V1_8
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

private val counter = AtomicInteger(0)
private val interfaceArray = ArrayList<String>()
        .apply {
            add(EventExecutor::class.java.name.replace('.', '/'))
        }
        .toTypedArray()

internal class ExecutorFactory<E: Any, L: Any> {
    private val executorLoader = WeakHashMap<Class<L>, GeneratedClassLoader>()

    internal fun generateExecutor(listener: L, executorMethod: Method, eventClass: Class<E>, handlerInfo: Subscribe): EventExecutor<E, L> {
        // Generate class loader instance for given listener class
        @Suppress("UNCHECKED_CAST")
        val owningClass = executorMethod.declaringClass as Class<L>
        val loader = executorLoader.computeIfAbsent(owningClass) { GeneratedClassLoader() }

        // Generate executor
        return ASMExecutor(newClassBasedExecutor(loader, listener, eventClass, executorMethod), owningClass, handlerInfo.priority)
    }
}

// This class exists to implement methods I couldn't be arsed to implement with ASM
internal class ASMExecutor<E: Any, L: Any>(private val asmDelegate: EventExecutor<E, L>, override val owningClass: Class<L>, override val priority: Priority): EventExecutor<E, L> {
    override fun fire(event: E) = asmDelegate.fire(event)
}

private fun <E: Any, L: Any> newClassBasedExecutor(loader: GeneratedClassLoader, listener: L, eventClass: Class<E>,
                                                   executorMethod: Method): EventExecutor<E, L> {
    val methodOwnerClass = executorMethod.declaringClass.name
    val methodOwnerClassInternal = methodOwnerClass.replace('.', '/')
    val eventClassName = eventClass.name
    val eventClassNameInternal = eventClassName.replace('.', '/')

    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
    val className = "${ExecutorFactory::class.java.`package`.name}.generated.ASMExecutor${counter.incrementAndGet()}"
    val internalName = className.replace('.', '/')
    writer.visit(V1_8, ACC_SUPER and ACC_PUBLIC, internalName, null, "java/lang/Object", interfaceArray)

    // Field to contain method owner instance
    if(!Modifier.isStatic(executorMethod.modifiers))
        writer.visitField(ACC_PRIVATE, "inst", "Ljava/lang/Object;", null, null).visitEnd()

    // Constructor descriptor
    // Pass owning method class instance
    val ctorDesc = "(${if(!Modifier.isStatic(executorMethod.modifiers)) "Ljava/lang/Object;" else ""})V"

    // ** Define constructor
    val ctor = writer.visitMethod(ACC_PUBLIC, "<init>", ctorDesc, null, null)

    // Call super
    ctor.visitVarInsn(ALOAD, 0)
    ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)

    // Store method owner instance
    if(!Modifier.isStatic(executorMethod.modifiers)) {
        ctor.visitVarInsn(ALOAD, 0)
        ctor.visitVarInsn(ALOAD, 1)
        ctor.visitFieldInsn(PUTFIELD, internalName, "inst", "Ljava/lang/Object;");
    }
    ctor.visitInsn(RETURN)
    ctor.visitMaxs(0, 0)
    ctor.visitEnd()

    // Implement fire(Ljava/lang/Object;)V method
    val fireMethod = writer.visitMethod(ACC_PUBLIC, "fire", "(Ljava/lang/Object;)V", null, null)
    if(!Modifier.isStatic(executorMethod.modifiers)) {
        fireMethod.visitVarInsn(ALOAD, 0)
        fireMethod.visitFieldInsn(GETFIELD, internalName, "inst", "Ljava/lang/Object;")
        fireMethod.visitTypeInsn(CHECKCAST, methodOwnerClassInternal)
    }
    fireMethod.visitVarInsn(ALOAD, 1)
    fireMethod.visitTypeInsn(CHECKCAST, eventClassNameInternal)
    fireMethod.visitMethodInsn(INVOKEVIRTUAL, methodOwnerClassInternal, executorMethod.name, "(${Type.getDescriptor(eventClass)})V",
            Modifier.isInterface(executorMethod.modifiers))
    fireMethod.visitInsn(RETURN)
    fireMethod.visitMaxs(0, 0)
    fireMethod.visitEnd()

    // Load class and construct it
    return loader.defineClass<EventExecutor<E, L>>(className, writer.toByteArray())
            .getConstructor(Any::class.java).apply { isAccessible = true }
            .newInstance(listener)
}