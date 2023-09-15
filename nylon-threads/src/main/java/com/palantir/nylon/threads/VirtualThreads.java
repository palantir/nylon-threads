/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.jetbrains.annotations.Nullable;

/**
 * Utility functionality to allow libraries which compile for earlier JDKs
 * to take advantage of virtual threads features on sufficiently new runtimes.
 */
public final class VirtualThreads {
    private static final SafeLogger log = SafeLoggerFactory.get(VirtualThreads.class);

    @Nullable
    private static final VirtualThreadSupport VIRTUAL_THREAD_SUPPORT = maybeInitialize();

    public static boolean isVirtual(Thread thread) {
        return VIRTUAL_THREAD_SUPPORT != null && VIRTUAL_THREAD_SUPPORT.isVirtual(thread);
    }

    public static Optional<VirtualThreadSupport> get() {
        return Optional.ofNullable(VIRTUAL_THREAD_SUPPORT);
    }

    @Nullable
    private static VirtualThreadSupport maybeInitialize() {
        int featureVersion = Runtime.version().feature();
        if (featureVersion < 21) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Virtual threads are not available prior to jdk21",
                        SafeArg.of("currentVersion", featureVersion));
            }
            return null;
        }
        try {
            return new ReflectiveVirtualThreadSupport();
        } catch (Throwable t) {
            log.warn("Virtual thread support is not available", t);
            return null;
        }
    }

    private VirtualThreads() {}

    public interface VirtualThreadSupport {
        /**
         * Returns {@code true} if {@code thread} is virtual, {@code false} otherwise.
         * Equivalent to {@code thread.isVirtual()} on jdk-21+.
         */
        boolean isVirtual(Thread thread);

        /** Equivalent to {@code Executors.newThreadPerTaskExecutor(threadFactory)} on jdk-21+. */
        ExecutorService newThreadPerTaskExecutor(ThreadFactory threadFactory);

        /** Alias to the jdk-21 {@code Thread.ofVirtual()} API. */
        VirtualThreadBuilder ofVirtual();

        interface VirtualThreadBuilder {
            VirtualThreadBuilder name(String name);

            VirtualThreadBuilder name(String prefix, long start);

            VirtualThreadBuilder inheritInheritableThreadLocals(boolean inherit);

            VirtualThreadBuilder uncaughtExceptionHandler(UncaughtExceptionHandler ueh);

            Thread unstarted(Runnable task);

            Thread start(Runnable task);

            ThreadFactory factory();
        }
    }

    private static final class ReflectiveVirtualThreadSupport implements VirtualThreadSupport {

        private static final MethodType THREAD_IS_VIRTUAL_TYPE = MethodType.methodType(boolean.class);

        private final MethodHandle threadIsVirtual;
        private final MethodHandle threadOfVirtual;
        private final MethodHandle ofVirtualFactory;
        private final MethodHandle ofVirtualNameString;
        private final MethodHandle ofVirtualNameStringLong;
        private final MethodHandle ofVirtualInheritInheritableThreadLocals;
        private final MethodHandle ofVirtualUncaughtExceptionHandler;
        private final MethodHandle ofVirtualUnstarted;
        private final MethodHandle ofVirtualStart;
        private final MethodHandle executorsNewThreadPerTaskExecutor;

        ReflectiveVirtualThreadSupport() throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            threadIsVirtual = lookup.findVirtual(Thread.class, "isVirtual", THREAD_IS_VIRTUAL_TYPE);
            Class<?> ofVirtual = lookup.findClass("java.lang.Thread$Builder$OfVirtual");
            MethodType threadOfVirtualType = MethodType.methodType(ofVirtual);
            threadOfVirtual = lookup.findStatic(Thread.class, "ofVirtual", threadOfVirtualType);

            MethodType ofVirtualFactoryType = MethodType.methodType(ThreadFactory.class);
            ofVirtualFactory = lookup.findVirtual(ofVirtual, "factory", ofVirtualFactoryType);

            MethodType ofVirtualNameStringType = MethodType.methodType(ofVirtual, String.class);
            ofVirtualNameString = lookup.findVirtual(ofVirtual, "name", ofVirtualNameStringType);
            MethodType ofVirtualNameStringLongType = MethodType.methodType(ofVirtual, String.class, long.class);
            ofVirtualNameStringLong = lookup.findVirtual(ofVirtual, "name", ofVirtualNameStringLongType);

            MethodType inheritInheritableThreadLocalsType = MethodType.methodType(ofVirtual, boolean.class);
            ofVirtualInheritInheritableThreadLocals =
                    lookup.findVirtual(ofVirtual, "inheritInheritableThreadLocals", inheritInheritableThreadLocalsType);

            MethodType uncaughtExceptionHandlerType = MethodType.methodType(ofVirtual, UncaughtExceptionHandler.class);
            ofVirtualUncaughtExceptionHandler =
                    lookup.findVirtual(ofVirtual, "uncaughtExceptionHandler", uncaughtExceptionHandlerType);

            MethodType ofVirtualThreadCreators = MethodType.methodType(Thread.class, Runnable.class);
            ofVirtualUnstarted = lookup.findVirtual(ofVirtual, "unstarted", ofVirtualThreadCreators);
            ofVirtualStart = lookup.findVirtual(ofVirtual, "start", ofVirtualThreadCreators);

            MethodType executorsNewThreadPerTaskExecutorType =
                    MethodType.methodType(ExecutorService.class, ThreadFactory.class);
            executorsNewThreadPerTaskExecutor = lookup.findStatic(
                    Executors.class, "newThreadPerTaskExecutor", executorsNewThreadPerTaskExecutorType);
        }

        @Override
        public boolean isVirtual(Thread thread) {
            try {
                return (boolean) threadIsVirtual.invokeExact(thread);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke 'thread.isVirtual()'", t);
            }
        }

        @Override
        public ExecutorService newThreadPerTaskExecutor(ThreadFactory threadFactory) {
            try {
                return (ExecutorService) executorsNewThreadPerTaskExecutor.invoke(threadFactory);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke 'Executors.newThreadPerTaskExecutor'", t);
            }
        }

        @Override
        @SuppressWarnings({"SafeLoggingPropagation", "IllegalSafeLoggingArgument"})
        public VirtualThreadBuilder ofVirtual() {
            try {
                return new ReflectiveVirtualThreadBuilder(threadOfVirtual.invoke());
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new SafeRuntimeException("failed to invoke 'Thread.ofVirtual()'", t);
            }
        }

        @SuppressWarnings("unused")
        private final class ReflectiveVirtualThreadBuilder implements VirtualThreadBuilder {

            private final Object ofVirtualDelegate;

            ReflectiveVirtualThreadBuilder(Object ofVirtualDelegate) {
                this.ofVirtualDelegate = ofVirtualDelegate;
            }

            @Override
            public VirtualThreadBuilder name(String name) {
                try {
                    ofVirtualNameString.invoke(ofVirtualDelegate, name);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new SafeRuntimeException("failed to invoke 'OfVirtual.name'", t);
                }
                return this;
            }

            @Override
            public VirtualThreadBuilder name(String prefix, long start) {
                try {
                    ofVirtualNameStringLong.invoke(ofVirtualDelegate, prefix, start);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new SafeRuntimeException("failed to invoke 'OfVirtual.name'", t);
                }
                return this;
            }

            @Override
            public VirtualThreadBuilder inheritInheritableThreadLocals(boolean inherit) {
                try {
                    ofVirtualInheritInheritableThreadLocals.invoke(ofVirtualDelegate, inherit);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new SafeRuntimeException("failed to invoke 'OfVirtual.inheritInheritableThreadLocals'", t);
                }
                return this;
            }

            @Override
            public VirtualThreadBuilder uncaughtExceptionHandler(UncaughtExceptionHandler ueh) {
                try {
                    ofVirtualUncaughtExceptionHandler.invoke(ofVirtualDelegate, ueh);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new SafeRuntimeException("failed to invoke 'OfVirtual.uncaughtExceptionHandler'", t);
                }
                return this;
            }

            @Override
            public Thread unstarted(Runnable task) {
                try {
                    return (Thread) ofVirtualUnstarted.invoke(ofVirtualDelegate, task);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new SafeRuntimeException("failed to invoke 'OfVirtual.unstarted'", t);
                }
            }

            @Override
            public Thread start(Runnable task) {
                try {
                    return (Thread) ofVirtualStart.invoke(ofVirtualDelegate, task);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new SafeRuntimeException("failed to invoke 'OfVirtual.start'", t);
                }
            }

            @Override
            public ThreadFactory factory() {
                try {
                    return (ThreadFactory) ofVirtualFactory.invoke(ofVirtualDelegate);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new SafeRuntimeException("failed to invoke 'OfVirtual.factory()'", t);
                }
            }
        }
    }
}
