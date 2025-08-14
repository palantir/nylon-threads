/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.nylon.threads;

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class ScopedValues {
    private static final SafeLogger log = SafeLoggerFactory.get(ScopedValues.class);

    private static final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    private static final Optional<Class<?>> IS_SUPPORTED = isSupported();

    private ScopedValues() {}

    public static <T> Optional<ScopedValueSupport<T>> newInstance() {
        return IS_SUPPORTED.map(clazz -> {
            try {
                return ReflectiveScopedValueSupport.newInstance(clazz);
            } catch (Throwable t) {
                log.warn("Scoped value support is not available", t);
                return null;
            }
        });
    }

    public static <T, R> R callWhere(ScopedValueSupport<T> key, T value, Callable<? extends R> op) {
        return withReflectiveScopedValueSupport(key, (clazz, k) -> {
            try {
                return ReflectiveScopedValueSupport.callWhere(clazz, k, value, op);
            } catch (Throwable t) {
                throw new SafeRuntimeException("Scoped value support is not available", t);
            }
        });
    }

    public static <T, R> R getWhere(ScopedValueSupport<T> key, T value, Supplier<? extends R> op) {
        return withReflectiveScopedValueSupport(key, (clazz, k) -> {
            try {
                return ReflectiveScopedValueSupport.getWhere(clazz, k, value, op);
            } catch (Throwable t) {
                throw new SafeRuntimeException("Scoped value support is not available", t);
            }
        });
    }

    public static <T> void runWhere(ScopedValueSupport<T> key, T value, Runnable op) {
        withReflectiveScopedValueSupport(key, (clazz, k) -> {
            try {
                ReflectiveScopedValueSupport.runWhere(clazz, k, value, op);
            } catch (Throwable t) {
                throw new SafeRuntimeException("Scoped value support is not available", t);
            }
        });
    }

    public static <T> ScopedValueSupport.Carrier where(ScopedValueSupport<T> key, T value) {
        return withReflectiveScopedValueSupport(key, (clazz, k) -> {
            try {
                return ReflectiveScopedValueSupport.where(clazz, k, value);
            } catch (Throwable t) {
                throw new SafeRuntimeException("Scoped value support is not available", t);
            }
        });
    }

    public interface ScopedValueSupport<T> {
        T get();

        boolean isBound();

        T orElse(T other);

        <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X;

        interface Carrier {
            <R> R call(Callable<? extends R> op);

            <T> T get(ScopedValueSupport<T> key);

            <R> R get(Supplier<? extends R> op);

            void run(Runnable op);

            <T> ScopedValueSupport.Carrier where(ScopedValueSupport<T> key, T value);
        }
    }

    private static final class ReflectiveScopedValueSupport<T> implements ScopedValueSupport<T> {
        private final Object scopedValue;
        private final MethodHandle scopedValueGet;
        private final MethodHandle scopedValueIsBound;
        private final MethodHandle scopedValueOrElse;
        private final MethodHandle scopedValueOrElseThrow;

        private ReflectiveScopedValueSupport(Class<?> clazz, Object scopedValue) throws ReflectiveOperationException {
            this.scopedValue = scopedValue;
            this.scopedValueGet = lookup.findVirtual(clazz, "get", MethodType.methodType(Object.class));
            this.scopedValueIsBound = lookup.findVirtual(clazz, "isBound", MethodType.methodType(boolean.class));
            this.scopedValueOrElse =
                    lookup.findVirtual(clazz, "orElse", MethodType.methodType(Object.class, Object.class));
            this.scopedValueOrElseThrow =
                    lookup.findVirtual(clazz, "orElseThrow", MethodType.methodType(Object.class, Supplier.class));
        }

        static <U> ReflectiveScopedValueSupport<U> newInstance(Class<?> clazz) throws ReflectiveOperationException {
            MethodHandle scopedValueNewInstance = lookup.findStatic(clazz, "newInstance", MethodType.methodType(clazz));
            try {
                Object instance = scopedValueNewInstance.invoke();
                return new ReflectiveScopedValueSupport<>(clazz, instance);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke 'ScopedValue#newInstance'", t);
            }
        }

        @SuppressWarnings("unchecked")
        static <U, R> R callWhere(
                Class<?> clazz, ReflectiveScopedValueSupport<U> key, U value, Callable<? extends R> op)
                throws ReflectiveOperationException {
            MethodHandle scopedValueCallWhere = lookup.findStatic(
                    clazz, "callWhere", MethodType.methodType(Object.class, clazz, Object.class, Callable.class));
            try {
                return (R) scopedValueCallWhere.invoke(key.scopedValue, value, op);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke 'ScopedValue#callWHere'", t);
            }
        }

        @SuppressWarnings("unchecked")
        static <U, R> R getWhere(Class<?> clazz, ReflectiveScopedValueSupport<U> key, U value, Supplier<? extends R> op)
                throws ReflectiveOperationException {
            MethodHandle scopedValueGetWhere = lookup.findStatic(
                    clazz, "getWhere", MethodType.methodType(Object.class, clazz, Object.class, Supplier.class));
            try {
                return (R) scopedValueGetWhere.invoke(key.scopedValue, value, op);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke 'ScopedValue#getWhere'", t);
            }
        }

        static <U> void runWhere(Class<?> clazz, ReflectiveScopedValueSupport<U> key, U value, Runnable op)
                throws ReflectiveOperationException {
            MethodHandle scopedValueRunWhere = lookup.findStatic(
                    clazz, "runWhere", MethodType.methodType(void.class, clazz, Object.class, Runnable.class));
            try {
                scopedValueRunWhere.invoke(key.scopedValue, value, op);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke 'ScopedValue#runWhere'", t);
            }
        }

        static <U> Carrier where(Class<?> scopedValueClass, ReflectiveScopedValueSupport<U> key, U value)
                throws ReflectiveOperationException {
            Class<?> carrierClass = lookup.findClass("java.lang.ScopedValue$Carrier");
            MethodHandle scopedValueWhere = lookup.findStatic(
                    scopedValueClass, "where", MethodType.methodType(carrierClass, scopedValueClass, Object.class));
            try {
                Object newCarrier = scopedValueWhere.invoke(key.scopedValue, value);
                return new ReflectiveCarrier(carrierClass, scopedValueClass, newCarrier);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke 'ScopedValue#where'", t);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public T get() {
            try {
                return (T) scopedValueGet.invoke(scopedValue);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke 'ScopedValue#get'", t);
            }
        }

        @Override
        public boolean isBound() {
            try {
                return (boolean) scopedValueIsBound.invoke(scopedValue);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke 'ScopedValue#isBound'", t);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public T orElse(T other) {
            try {
                return (T) scopedValueOrElse.invoke(scopedValue, other);
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke 'ScopedValue#orElse'", t);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
            try {
                return (T) scopedValueOrElseThrow.invoke(scopedValue, exceptionSupplier);
            } catch (WrongMethodTypeException | ClassCastException e) {
                throw new SafeRuntimeException("failed to invoke 'ScopedValue#orElseThrow'", e);
            } catch (Throwable t) {
                // Throwable must propagate to caller in this case
                throw (X) t;
            }
        }

        private static final class ReflectiveCarrier implements ScopedValueSupport.Carrier {
            private final Object carrier;
            private final Class<?> carrierClass;
            private final Class<?> scopedValueClass;
            private final MethodHandle carrierCall;
            private final MethodHandle carrierGetScopedValue;
            private final MethodHandle carrierGetSupplier;
            private final MethodHandle carrierRun;
            private final MethodHandle carrierWhere;

            private ReflectiveCarrier(Class<?> carrierClass, Class<?> scopedValueClass, Object carrier)
                    throws ReflectiveOperationException {
                this.carrier = carrier;
                this.carrierClass = carrierClass;
                this.scopedValueClass = scopedValueClass;
                this.carrierCall =
                        lookup.findVirtual(carrierClass, "call", MethodType.methodType(Object.class, Callable.class));
                this.carrierGetScopedValue =
                        lookup.findVirtual(carrierClass, "get", MethodType.methodType(Object.class, scopedValueClass));
                this.carrierGetSupplier =
                        lookup.findVirtual(carrierClass, "get", MethodType.methodType(Object.class, Supplier.class));
                this.carrierRun =
                        lookup.findVirtual(carrierClass, "run", MethodType.methodType(void.class, Runnable.class));
                this.carrierWhere = lookup.findVirtual(
                        carrierClass, "where", MethodType.methodType(carrierClass, scopedValueClass, Object.class));
            }

            @Override
            @SuppressWarnings("unchecked")
            public <R> R call(Callable<? extends R> op) {
                try {
                    return (R) carrierCall.invoke(carrier, op);
                } catch (Throwable t) {
                    throw new SafeRuntimeException("failed to invoke 'ScopedValue.Carrier#call'", t);
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T get(ScopedValueSupport<T> key) {
                return withReflectiveScopedValueSupport(key, (_clazz, k) -> {
                    try {
                        return (T) carrierGetScopedValue.invoke(carrier, k.scopedValue);
                    } catch (Throwable t) {
                        throw new SafeRuntimeException(
                                "failed to invoke 'ScopedValue.Carrier#get' with scoped value", t);
                    }
                });
            }

            @Override
            @SuppressWarnings("unchecked")
            public <R> R get(Supplier<? extends R> op) {
                try {
                    return (R) carrierGetSupplier.invoke(carrier, op);
                } catch (Throwable t) {
                    throw new SafeRuntimeException("failed to invoke 'ScopedValue.Carrier#get' with supplier", t);
                }
            }

            @Override
            public void run(Runnable op) {
                try {
                    carrierRun.invoke(carrier, op);
                } catch (Throwable t) {
                    throw new SafeRuntimeException("failed to invoke 'ScopedValue.Carrier#run'", t);
                }
            }

            @Override
            public <T> ScopedValueSupport.Carrier where(ScopedValueSupport<T> key, T value) {
                return withReflectiveScopedValueSupport(key, (_clazz, k) -> {
                    try {
                        Object newCarrier = carrierWhere.invoke(carrier, k.scopedValue, value);
                        return new ReflectiveCarrier(carrierClass, scopedValueClass, newCarrier);
                    } catch (Throwable t) {
                        throw new SafeRuntimeException("failed to invoke 'ScopedValue.Carrier#where'", t);
                    }
                });
            }
        }
    }

    private static Optional<Class<?>> isSupported() {
        int featureVersion = Runtime.version().feature();
        if (featureVersion < 21) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Scoped values ore not available prior to jdk21", SafeArg.of("currentVersion", featureVersion));
            }
            return Optional.empty();
        }
        // scoped values are a preview feature in jdk >= 21, and will become final in jdk 25.
        // check if they're available by truing to load java.lang.ScopedValue via reflection
        // note that the JVM appears to allow loading classes from preview APIs via reflection
        // even if the JVM was launched _without_ `--enable-preview`. We may want to consider limiting
        // support to only jdk >= 25, as that is the first release where ScopedValue will be considered
        // a production-ready feature.
        try {
            return Optional.of(lookup.findClass("java.lang.ScopedValue"));
        } catch (ClassNotFoundException | IllegalAccessException e) {
            log.warn("Scoped value support is not available", e);
            return Optional.empty();
        }
    }

    private interface WithReflectiveScopedValueSupport<T, R> {
        R get(Class<?> clazz, ReflectiveScopedValueSupport<T> v);
    }

    private static <T, R> R withReflectiveScopedValueSupport(
            ScopedValueSupport<T> v, WithReflectiveScopedValueSupport<T, R> op) {
        if (IS_SUPPORTED.isPresent()) {
            if (v instanceof ReflectiveScopedValueSupport<T> r) {
                return op.get(IS_SUPPORTED.get(), r);
            }
        }
        throw new SafeRuntimeException("Scoped value support is not available");
    }

    private static <T> void withReflectiveScopedValueSupport(
            ScopedValueSupport<T> v, BiConsumer<Class<?>, ReflectiveScopedValueSupport<T>> op) {
        if (IS_SUPPORTED.isPresent()) {
            if (v instanceof ReflectiveScopedValueSupport<T> r) {
                op.accept(IS_SUPPORTED.get(), r);
                return;
            }
        }
        throw new SafeRuntimeException("Scoped value support is not available");
    }
}
