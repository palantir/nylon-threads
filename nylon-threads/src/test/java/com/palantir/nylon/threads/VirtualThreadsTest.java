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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.Runnables;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.nylon.threads.VirtualThreads.VirtualThreadSupport;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * These tests are primarily meant to exercise each method to ensure calls are properly delegated. We assume
 * that if data is passed to the underlying APIs, those APIs are implemented correctly.
 */
class VirtualThreadsTest {

    @Test
    public void nonVirtualThread() {
        assertThat(VirtualThreads.isVirtual(new Thread())).isFalse();
    }

    @Test
    public void virtualThreadSupportIsPresent() {
        assertThat(VirtualThreads.get()).isPresent();
    }

    @Test
    public void factory() throws ExecutionException, InterruptedException {
        VirtualThreadSupport support = VirtualThreads.get().orElseThrow();
        SettableFuture<Boolean> threadIsVirtual = SettableFuture.create();
        Thread thread = support.ofVirtual()
                .factory()
                .newThread(() -> threadIsVirtual.set(support.isVirtual(Thread.currentThread())));
        thread.start();
        assertThat(threadIsVirtual.get()).isTrue();
    }

    @Test
    public void name() {
        String expected = "my-thread";
        Thread thread =
                VirtualThreads.get().orElseThrow().ofVirtual().name(expected).unstarted(Runnables.doNothing());
        assertThat(thread.getName()).isEqualTo(expected);
    }

    @Test
    public void namePrefix() {
        Thread thread = VirtualThreads.get()
                .orElseThrow()
                .ofVirtual()
                .name("my-thread-", 0)
                .unstarted(Runnables.doNothing());
        assertThat(thread.getName()).isEqualTo("my-thread-0");
    }

    @Test
    public void inheritedThreadLocalsEnabled() throws ExecutionException, InterruptedException {
        SettableFuture<String> result = SettableFuture.create();
        InheritableThreadLocal<String> inheritableThreadLocal = new InheritableThreadLocal<>();
        inheritableThreadLocal.set("parent-thread");
        VirtualThreads.get()
                .orElseThrow()
                .ofVirtual()
                .inheritInheritableThreadLocals(true)
                .start(() -> result.set(
                        Optional.ofNullable(inheritableThreadLocal.get()).orElse("none")));
        assertThat(result.get()).isEqualTo("parent-thread");
    }

    @Test
    public void inheritedThreadLocalsDisabled() throws ExecutionException, InterruptedException {
        SettableFuture<String> result = SettableFuture.create();
        InheritableThreadLocal<String> inheritableThreadLocal = new InheritableThreadLocal<>();
        inheritableThreadLocal.set("parent-thread");
        VirtualThreads.get()
                .orElseThrow()
                .ofVirtual()
                .inheritInheritableThreadLocals(false)
                .start(() -> result.set(
                        Optional.ofNullable(inheritableThreadLocal.get()).orElse("none")));
        assertThat(result.get()).isEqualTo("none");
    }

    @Test
    public void uncaughtExceptionHandler() {
        UncaughtExceptionHandler customExceptionHandler = (_thread, _throwable) -> {};
        Thread thread = VirtualThreads.get()
                .orElseThrow()
                .ofVirtual()
                .uncaughtExceptionHandler(customExceptionHandler)
                .unstarted(Runnables.doNothing());
        assertThat(thread.getUncaughtExceptionHandler()).isSameAs(customExceptionHandler);
    }

    @Test
    public void unstarted() {
        Thread thread = VirtualThreads.get().orElseThrow().ofVirtual().unstarted(Runnables.doNothing());
        assertThat(thread.getState()).isEqualTo(Thread.State.NEW);
    }

    @Test
    public void start() {
        Thread thread = VirtualThreads.get().orElseThrow().ofVirtual().start(Runnables.doNothing());
        Awaitility.waitAtMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(thread.getState()).isEqualTo(Thread.State.TERMINATED));
    }

    @Test
    void newThreadPerTaskExecutor() {
        VirtualThreadSupport support = VirtualThreads.get().orElseThrow();
        ExecutorService exec =
                support.newThreadPerTaskExecutor(support.ofVirtual().factory());
        assertThat(exec).isNotNull();
    }
}
