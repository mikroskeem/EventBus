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

import eu.mikroskeem.eventbus.EventExecutor
import eu.mikroskeem.eventbus.annotations.Subscribe
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.CHECKCAST
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.Opcodes.PUTFIELD
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Opcodes.V1_8
import org.objectweb.asm.Type
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
        return newClassBasedExecutor(loader, listener, eventClass, executorMethod, handlerInfo)
    }
}

private fun <E: Any, L: Any> newClassBasedExecutor(loader: GeneratedClassLoader, listener: L, eventClass: Class<E>,
                                                   executorMethod: Method, handlerInfo: Subscribe): EventExecutor<E, L> {
    val methodOwnerClass = executorMethod.declaringClass.name
    val methodOwnerClassInternal = methodOwnerClass.replace('.', '/')
    val eventClassName = eventClass.name
    val eventClassNameInternal = eventClassName.replace('.', '/')

    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
    val className = "${ExecutorFactory::class.java.`package`.name}.generated.ASMExecutor${counter.incrementAndGet()}"
    val internalName = className.replace('.', '/')
    writer.visit(V1_8, ACC_SUPER and ACC_PUBLIC, internalName, null, "java/lang/Object", interfaceArray)

    // Field to contain method owner instance
    writer.visitField(ACC_PRIVATE, "inst", "Ljava/lang/Object;", null, null).visitEnd()

    // Field to contain priority
    writer.visitField(ACC_PRIVATE, "priority", "I", null, handlerInfo.priority).visitEnd()

    // Field to contain target method
    writer.visitField(ACC_PRIVATE, "target", "Ljava/lang/reflect/Method;", null, null).visitEnd()

    // ** Define constructor
    val ctor = writer.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/Object;Ljava/lang/reflect/Method;)V", null, null)

    // Call super
    ctor.visitVarInsn(ALOAD, 0)
    ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)

    // Store method owner instance
    ctor.visitVarInsn(ALOAD, 0)
    ctor.visitVarInsn(ALOAD, 1)
    ctor.visitFieldInsn(PUTFIELD, internalName, "inst", "Ljava/lang/Object;")

    // Store target method reference
    ctor.visitVarInsn(ALOAD, 0)
    ctor.visitVarInsn(ALOAD, 2)
    ctor.visitFieldInsn(PUTFIELD, internalName, "target", "Ljava/lang/reflect/Method;")

    ctor.visitInsn(RETURN)
    ctor.visitMaxs(0, 0)
    ctor.visitEnd()

    // ** Implement getPriority()I method
    val getPriorityMethod = writer.visitMethod(ACC_PUBLIC, "getPriority", "()I", null, null)
    getPriorityMethod.visitVarInsn(ALOAD, 0)
    getPriorityMethod.visitFieldInsn(GETFIELD, internalName, "priority", "I")
    getPriorityMethod.visitInsn(IRETURN)
    getPriorityMethod.visitMaxs(0, 0)
    getPriorityMethod.visitEnd()

    // ** Implement getOwningClass()Ljava/lang/Class;
    val getOwningClassMethod = writer.visitMethod(ACC_PUBLIC, "getOwningClass", "()Ljava/lang/Class;", null, null)
    getOwningClassMethod.visitVarInsn(ALOAD, 0)
    getOwningClassMethod.visitFieldInsn(GETFIELD, internalName, "inst", "Ljava/lang/Object;")
    getOwningClassMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false)
    getOwningClassMethod.visitInsn(ARETURN)
    getOwningClassMethod.visitMaxs(0, 0)
    getOwningClassMethod.visitEnd()

    // ** Implement getTargetMethod()Ljava/lang/reflect/Method;
    val getTargetMethod = writer.visitMethod(ACC_PUBLIC, "getTargetMethod", "()Ljava/lang/reflect/Method;", null, null)
    getTargetMethod.visitVarInsn(ALOAD, 0)
    getTargetMethod.visitFieldInsn(GETFIELD, internalName, "target", "Ljava/lang/reflect/Method;")
    getTargetMethod.visitInsn(ARETURN)
    getTargetMethod.visitMaxs(0, 0)
    getTargetMethod.visitEnd()

    // ** Implement fire(Ljava/lang/Object;)V method
    val fireMethod = writer.visitMethod(ACC_PUBLIC, "fire", "(Ljava/lang/Object;)V", null, null)
    fireMethod.visitVarInsn(ALOAD, 0)
    fireMethod.visitFieldInsn(GETFIELD, internalName, "inst", "Ljava/lang/Object;")
    fireMethod.visitTypeInsn(CHECKCAST, methodOwnerClassInternal)
    fireMethod.visitVarInsn(ALOAD, 1)
    fireMethod.visitTypeInsn(CHECKCAST, eventClassNameInternal)
    fireMethod.visitMethodInsn(INVOKEVIRTUAL, methodOwnerClassInternal, executorMethod.name, "(${Type.getDescriptor(eventClass)})V",
            Modifier.isInterface(executorMethod.modifiers))
    fireMethod.visitInsn(RETURN)
    fireMethod.visitMaxs(0, 0)
    fireMethod.visitEnd()

    // Load class and construct it
    return loader.defineClass<EventExecutor<E, L>>(className, writer.toByteArray())
            .getConstructor(Any::class.java, Method::class.java).apply { isAccessible = true }
            .newInstance(listener, executorMethod)
}