/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.bench.java.lang.foreign;

import java.lang.foreign.*;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

import static java.lang.invoke.MethodHandles.lookup;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = { "--enable-native-access=ALL-UNNAMED", "--enable-preview" })
public class Upcalls extends CLayouts {

    static final Linker abi = Linker.nativeLinker();
    static final MethodHandle blank;
    static final MethodHandle identity;
    static final MethodHandle args5;
    static final MethodHandle args10;

    static final MemorySegment cb_blank;
    static final MemorySegment cb_identity;
    static final MemorySegment cb_args5;
    static final MemorySegment cb_args10;

    static final long cb_blank_jni;
    static final long cb_identity_jni;
    static final long cb_args5_jni;
    static final long cb_args10_jni;

    static {
        System.loadLibrary("UpcallsJNI");

        String className = "org/openjdk/bench/java/lang/foreign/Upcalls";
        cb_blank_jni = JNICB.makeCB(className, "blank", "()V");
        cb_identity_jni = JNICB.makeCB(className, "identity", "(I)I");
        cb_args5_jni = JNICB.makeCB(className, "args5", "(JDJDJ)V");
        cb_args10_jni = JNICB.makeCB(className, "args10", "(JDJDJDJDJD)V");

        try {
            System.loadLibrary("Upcalls");
            {
                String name = "blank";
                MethodType mt = MethodType.methodType(void.class);
                FunctionDescriptor fd = FunctionDescriptor.ofVoid();

                blank = linkFunc(name, fd);
                cb_blank = makeCB(name, mt, fd);
            }
            {
                String name = "identity";
                MethodType mt = MethodType.methodType(int.class, int.class);
                FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_INT);

                identity = linkFunc(name, fd);
                cb_identity = makeCB(name, mt, fd);
            }
            {
                String name = "args5";
                MethodType mt = MethodType.methodType(void.class,
                        long.class, double.class, long.class, double.class, long.class);
                FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                        C_LONG_LONG, C_DOUBLE, C_LONG_LONG, C_DOUBLE, C_LONG_LONG);

                args5 = linkFunc(name, fd);
                cb_args5 = makeCB(name, mt, fd);
            }
            {
                String name = "args10";
                MethodType mt = MethodType.methodType(void.class,
                        long.class, double.class, long.class, double.class, long.class,
                        double.class, long.class, double.class, long.class, double.class);
                FunctionDescriptor fd = FunctionDescriptor.ofVoid(
                        C_LONG_LONG, C_DOUBLE, C_LONG_LONG, C_DOUBLE, C_LONG_LONG,
                        C_DOUBLE, C_LONG_LONG, C_DOUBLE, C_LONG_LONG, C_DOUBLE);

                args10 = linkFunc(name, fd);
                cb_args10 = makeCB(name, mt, fd);
            }
        } catch (ReflectiveOperationException e) {
            throw new BootstrapMethodError(e);
        }
    }

    static MethodHandle linkFunc(String name, FunctionDescriptor baseDesc) {
        return abi.downcallHandle(
                SymbolLookup.loaderLookup().find(name).orElseThrow(),
                baseDesc.appendArgumentLayouts(C_POINTER)
        );
    }

    static MemorySegment makeCB(String name, MethodType mt, FunctionDescriptor fd) throws ReflectiveOperationException {
        return abi.upcallStub(
            lookup().findStatic(Upcalls.class, name, mt),
            fd, Arena.global()
        );
    }

    static native void blank(long cb);
    static native int identity(int x, long cb);
    static native void args5(long a0, double a1, long a2, double a3, long a4, long cb);
    static native void args10(long a0, double a1, long a2, double a3, long a4,
                              double a5, long a6, double a7, long a8, double a9, long cb);

    @Benchmark
    public void jni_blank() throws Throwable {
        blank(cb_blank_jni);
    }

    @Benchmark
    public void panama_blank() throws Throwable {
        blank.invokeExact(cb_blank);
    }

    @Benchmark
    public int jni_identity() throws Throwable {
        return identity(10, cb_identity_jni);
    }

    @Benchmark
    public void jni_args5() throws Throwable {
        args5(1L, 2D, 3L, 4D, 5L, cb_args5_jni);
    }

    @Benchmark
    public void jni_args10() throws Throwable {
        args10(1L, 2D, 3L, 4D, 5L, 6D, 7L, 8D, 9L, 10D, cb_args10_jni);
    }

    @Benchmark
    public int panama_identity() throws Throwable {
        return (int) identity.invokeExact(10, cb_identity);
    }

    @Benchmark
    public void panama_args5() throws Throwable {
        args5.invokeExact(1L, 2D, 3L, 4D, 5L, cb_args5);
    }

    @Benchmark
    public void panama_args10() throws Throwable {
        args10.invokeExact(1L, 2D, 3L, 4D, 5L, 6D, 7L, 8D, 9L, 10D, cb_args10);
    }

    static void blank() {}
    static int identity(int x) { return x; }
    static void args5(long a0, double a1, long a2, double a3, long a4) { }
    static void args10(long a0, double a1, long a2, double a3, long a4,
                       double a5, long a6, double a7, long a8, double a9) { }
}
