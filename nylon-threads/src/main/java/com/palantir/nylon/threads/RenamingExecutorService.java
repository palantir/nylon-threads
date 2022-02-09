/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** This {@link ExecutorService} wrapper renames threads for the duration of task execution. */
final class RenamingExecutorService extends AbstractExecutorService {

    private final ExecutorService delegate;

    private final Thread.UncaughtExceptionHandler handler;

    private final Supplier<String> nameSupplier;

    RenamingExecutorService(
            ExecutorService delegate, Thread.UncaughtExceptionHandler handler, Supplier<String> nameSupplier) {
        this.delegate = Preconditions.checkNotNull(delegate, "delegate");
        this.nameSupplier = Preconditions.checkNotNull(nameSupplier, "nameSupplier");
        this.handler = Preconditions.checkNotNull(handler, "handler");
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(new RenamingRunnable(command));
    }

    @Override
    public String toString() {
        return "RenamingExecutorService{delegate=" + delegate + '}';
    }

    final class RenamingRunnable implements Runnable {

        private final Runnable command;

        RenamingRunnable(Runnable command) {
            this.command = command;
        }

        @Override
        public void run() {
            final Thread currentThread = Thread.currentThread();
            final String originalName = currentThread.getName();
            ThreadNames.setThreadName(currentThread, nameSupplier.get());
            try {
                command.run();
            } catch (Throwable t) {
                handler.uncaughtException(currentThread, t);
            } finally {
                ThreadNames.setThreadName(currentThread, originalName);
            }
        }
    }

    static Supplier<String> threadNameSupplier(String baseName) {
        String prefix = Preconditions.checkNotNull(baseName, "Base name is required") + '-';
        AtomicLong index = new AtomicLong();
        ThreadLocal<String> threadNameCache = ThreadLocal.withInitial(() -> prefix + index.getAndIncrement());
        return threadNameCache::get;
    }
}
