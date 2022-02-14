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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.MoreExecutors;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class NylonExecutorTest {

    @Test
    void testThreadName() {
        String originalThreadName = Thread.currentThread().getName();
        ExecutorService executor = NylonExecutor.builder()
                .name("foo")
                .executor(MoreExecutors.directExecutor())
                .build();

        assertThat(executor.submit(() -> {
                    assertThat(Thread.currentThread().getName()).isEqualTo("foo-0");
                    return Boolean.TRUE;
                }))
                .succeedsWithin(Duration.ZERO);

        assertThat(Thread.currentThread().getName())
                .as("Thread names should not be tainted")
                .isEqualTo(originalThreadName);
    }

    @Test
    void testUncaughtExceptionHandler() {
        String originalThreadName = Thread.currentThread().getName();
        List<String> uncaughtExceptionHandlerThreadNames = new CopyOnWriteArrayList<>();
        ExecutorService executor = NylonExecutor.builder()
                .name("foo")
                .executor(MoreExecutors.directExecutor())
                .uncaughtExceptionHandler(
                        (thread, _throwable) -> uncaughtExceptionHandlerThreadNames.add(thread.getName()))
                .build();
        executor.execute(() -> {
            throw new IllegalStateException();
        });

        assertThat(Thread.currentThread().getName())
                .as("Thread names should not be tainted")
                .isEqualTo(originalThreadName);
        assertThat(uncaughtExceptionHandlerThreadNames)
                .as("Uncaught exception handler must be called exactly once while the thread is renamed")
                .containsExactly("foo-0");
    }

    @Test
    void testThreadNamesAreReusedWhenDelegateThreadsAreReused() {
        ExecutorService delegate = Executors.newFixedThreadPool(1);
        try {
            ExecutorService executor =
                    NylonExecutor.builder().name("foo").executor(delegate).build();

            List<String> observedThreadNames = new CopyOnWriteArrayList<>();

            for (int i = 0; i < 2; i++) {
                assertThat(executor.submit(() -> {
                            observedThreadNames.add(Thread.currentThread().getName());
                            return Boolean.TRUE;
                        }))
                        .succeedsWithin(Duration.ofSeconds(1));
            }
            assertThat(observedThreadNames).hasSize(2).allSatisfy(name -> assertThat(name)
                    .isEqualTo("foo-0"));
        } finally {
            assertThat(MoreExecutors.shutdownAndAwaitTermination(delegate, Duration.ofSeconds(1)))
                    .as("Delegate failed to stop")
                    .isTrue();
        }
    }

    @Test
    void testThreadNamesAreUniqueWhenDelegateThreadsAreUnique() throws InterruptedException {
        ExecutorService delegate = Executors.newCachedThreadPool();
        try {
            ExecutorService executor =
                    NylonExecutor.builder().name("foo").executor(delegate).build();

            Set<String> observedThreadNames = ConcurrentHashMap.newKeySet();
            CountDownLatch latch = new CountDownLatch(1);
            int threads = 2;
            CountDownLatch waitingLatch = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    observedThreadNames.add(Thread.currentThread().getName());
                    // Prevent the task from exiting after recording thread names, otherwise
                    // it's possible that both tasks will execute on the same thread.
                    waitingLatch.countDown();
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            waitingLatch.await();
            latch.countDown();
            assertThat(observedThreadNames).hasSize(threads).allSatisfy(name -> assertThat(name)
                    .startsWith("foo-"));
        } finally {
            assertThat(MoreExecutors.shutdownAndAwaitTermination(delegate, Duration.ofSeconds(1)))
                    .as("Delegate failed to stop")
                    .isTrue();
        }
    }
}
