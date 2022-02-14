/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.jboss.threads.ViewExecutor;

/** Factory utility to create an executor facade to share access to a single delegate executor. */
public final class NylonExecutor {

    public static NameStage builder() {
        return new Builder();
    }

    public interface NameStage {
        /**
         * Configure the executor name. Thread names will take the form {@code [name]-[counter]}. For example
         * an executor named {@code foo} will execute tasks on threads named {@code foo-0}, {@code foo-1}, etc.
         */
        ExecutorStage name(String name);
    }

    public interface ExecutorStage {
        /**
         * Sets the delegate {@link Executor} on which tasks are executed. This executor should be a 'cached' executor,
         * meaning there is no queue and all tasks are started immediately.
         */
        MaxThreadsStage executor(Executor executor);
    }

    public interface MaxThreadsStage extends BuildStage {
        /**
         * Configures the maximum threads allowed for concurrent execution through this executor facade. If no value is
         * specified, the default is {@link Integer#MAX_VALUE}, matching the standard-library
         * Executors factory.
         */
        QueueSizeStage maxThreads(int maxThreads);
    }

    public interface QueueSizeStage extends BuildStage {
        /**
         * Configures the maximum queue size for this executor facade. If no value is
         * specified, the default is {@link Integer#MAX_VALUE}, matching the standard-library
         * Executors factory.
         */
        BuildStage queueSize(int maxQueueSize);
    }

    public interface BuildStage {
        /**
         * Provide an {@link java.lang.Thread.UncaughtExceptionHandler}, otherwise a
         * {@link LoggingUncaughtExceptionHandler default logging implementation} will be used.
         */
        BuildStage uncaughtExceptionHandler(Thread.UncaughtExceptionHandler exceptionHandler);

        ExecutorService build();
    }

    private NylonExecutor() {}

    private static final class Builder
            implements NameStage, ExecutorStage, MaxThreadsStage, QueueSizeStage, BuildStage {

        private String name;
        private Executor executor;
        private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
        private OptionalInt maxThreads = OptionalInt.empty();
        private OptionalInt queueSize = OptionalInt.empty();
        private boolean built;

        @Override
        public ExecutorStage name(String value) {
            Preconditions.checkState(name == null, "Name has already been configured");
            name = Preconditions.checkNotNull(value, "Name is required");
            return this;
        }

        @Override
        public MaxThreadsStage executor(Executor value) {
            Preconditions.checkState(executor == null, "Executor has already been configured");
            this.executor = Preconditions.checkNotNull(value, "Executor is required");
            return this;
        }

        @Override
        public QueueSizeStage maxThreads(int value) {
            Preconditions.checkState(maxThreads.isEmpty(), "maxThreads has already been configured");
            if (value <= 0) {
                throw new SafeIllegalArgumentException("maxThreads must be positive");
            }
            maxThreads = OptionalInt.of(value);
            return this;
        }

        @Override
        public BuildStage queueSize(int value) {
            Preconditions.checkState(queueSize.isEmpty(), "queueSize has already been configured");
            if (value < 0) {
                throw new SafeIllegalArgumentException("queueSize cannot be negative");
            }
            queueSize = OptionalInt.of(value);
            return this;
        }

        @Override
        public BuildStage uncaughtExceptionHandler(Thread.UncaughtExceptionHandler value) {
            Preconditions.checkState(uncaughtExceptionHandler == null, "Exception handler has already been configured");
            uncaughtExceptionHandler = Preconditions.checkNotNull(value, "Exception handler is required");
            return this;
        }

        @Override
        public ExecutorService build() {
            Preconditions.checkState(!built, "Builder has already been used");
            built = true;
            Thread.UncaughtExceptionHandler handler =
                    Objects.requireNonNullElse(uncaughtExceptionHandler, LoggingUncaughtExceptionHandler.INSTANCE);
            int maxThreadsValue = maxThreads.orElse(Integer.MAX_VALUE);
            // If max-threads is Integer.MAX_VALUE, there's no need for a queue.
            int queueSizeValue = maxThreadsValue == Integer.MAX_VALUE ? 0 : queueSize.orElse(0);
            return new RenamingExecutorService(
                    ViewExecutor.builder(Preconditions.checkNotNull(executor, "Executor is required"))
                            .setMaxSize(maxThreadsValue)
                            .setQueueLimit(queueSizeValue)
                            .setUncaughtHandler(handler)
                            .build(),
                    handler,
                    RenamingExecutorService.threadNameSupplier(Preconditions.checkNotNull(name, "Name is required")));
        }
    }
}
