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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Streams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
                    return true;
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
                            return true;
                        }))
                        .succeedsWithin(Duration.ofSeconds(1));
            }
            assertThat(observedThreadNames)
                    .hasSize(2)
                    .allSatisfy(name -> assertThat(name).isEqualTo("foo-0"));
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
            assertThat(observedThreadNames)
                    .hasSize(threads)
                    .allSatisfy(name -> assertThat(name).startsWith("foo-"));
        } finally {
            assertThat(MoreExecutors.shutdownAndAwaitTermination(delegate, Duration.ofSeconds(1)))
                    .as("Delegate failed to stop")
                    .isTrue();
        }
    }

    @Test
    void testFixedSizeExecutorAllowsQueueing() throws InterruptedException {
        ExecutorService delegate = Executors.newCachedThreadPool();
        try {
            ExecutorService executor = NylonExecutor.builder()
                    .name("foo")
                    .executor(delegate)
                    .maxThreads(1)
                    .build();

            CountDownLatch latch = new CountDownLatch(1);
            int queuedTasks = 100;
            CountDownLatch waitingLatch = new CountDownLatch(1);
            AtomicInteger completed = new AtomicInteger();

            executor.execute(() -> {
                waitingLatch.countDown();
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                completed.incrementAndGet();
            });
            // Subsequent tasks should be queued
            for (int i = 0; i < 100; i++) {
                executor.execute(completed::incrementAndGet);
            }

            waitingLatch.await();
            assertThat(completed)
                    .as("Tasks should all be queued behind the initial waiting task")
                    .hasValue(0);
            latch.countDown();
            Awaitility.waitAtMost(Duration.ofSeconds(1))
                    .untilAsserted(() -> assertThat(completed).hasValue(1 + queuedTasks));
        } finally {
            assertThat(MoreExecutors.shutdownAndAwaitTermination(delegate, Duration.ofSeconds(1)))
                    .as("Delegate failed to stop")
                    .isTrue();
        }
    }

    @Test
    void testMaxQueueSize() throws InterruptedException {
        ExecutorService delegate = Executors.newCachedThreadPool();
        try {
            ExecutorService executor = NylonExecutor.builder()
                    .name("foo")
                    .executor(delegate)
                    .maxThreads(1)
                    .queueSize(1)
                    .build();

            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch waitingLatch = new CountDownLatch(1);
            AtomicInteger completed = new AtomicInteger();

            executor.execute(() -> {
                waitingLatch.countDown();
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                completed.incrementAndGet();
            });
            // Subsequent task should be queued
            executor.execute(completed::incrementAndGet);
            // Queue is full, executor should throw
            assertThatThrownBy(() -> executor.execute(completed::incrementAndGet))
                    .isInstanceOf(RejectedExecutionException.class);

            waitingLatch.await();
            assertThat(completed)
                    .as("Tasks should all be queued behind the initial waiting task")
                    .hasValue(0);
            // Unblocking the first task will allow the second task to execute immediately after.
            latch.countDown();
            Awaitility.waitAtMost(Duration.ofSeconds(1))
                    .untilAsserted(() -> assertThat(completed).hasValue(2));
        } finally {
            assertThat(MoreExecutors.shutdownAndAwaitTermination(delegate, Duration.ofSeconds(1)))
                    .as("Delegate failed to stop")
                    .isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void testMaxThreadsLimitsConcurrentlyRunningThreads(int maxThreads) {
        AtomicInteger threadsCreated = new AtomicInteger();
        ExecutorService delegate = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("test-%d")
                .setThreadFactory(r -> {
                    int id = threadsCreated.incrementAndGet();
                    Thread thread = Executors.defaultThreadFactory().newThread(r);
                    thread.setName("thread-" + id);
                    return thread;
                })
                .build());
        try {
            CountDownLatch countDownLatch = new CountDownLatch(maxThreads);
            int tasks = 3 * maxThreads;
            List<ListenableFuture<String>> futures = new ArrayList<>(tasks);
            List<Instant> threadStarts = Collections.synchronizedList(new ArrayList<>(maxThreads));
            List<Instant> threadEnds = Collections.synchronizedList(new ArrayList<>(maxThreads));
            ListeningExecutorService executor = MoreExecutors.listeningDecorator(NylonExecutor.builder()
                    .name("foo")
                    .executor(delegate)
                    .maxThreads(maxThreads)
                    .build());
            for (int i = 0; i < tasks; i++) {
                futures.add(executor.submit(() -> {
                    threadStarts.add(Instant.now());
                    countDownLatch.countDown();
                    countDownLatch.await();
                    Thread thread = Thread.currentThread();
                    threadEnds.add(Instant.now());
                    return thread.getName();
                }));
            }
            assertThat(Futures.successfulAsList(futures))
                    .succeedsWithin(Duration.ofSeconds(10))
                    .asInstanceOf(InstanceOfAssertFactories.list(String.class))
                    .hasSize(tasks)
                    .allSatisfy(value -> assertThat(value).startsWith("foo-"));
            ThreadPeak threadPeak = peakThreads(threadStarts, threadEnds);
            assertThat(threadPeak.peak)
                    .as(
                            "should have at most %s threads running at once. Starts: %s. Ends: %s. List: %s",
                            maxThreads, threadStarts, threadEnds, threadPeak.description)
                    .isLessThanOrEqualTo(maxThreads);
            assertThat(delegate)
                    .asInstanceOf(InstanceOfAssertFactories.type(ThreadPoolExecutor.class))
                    .satisfies(threadPoolExecutor -> {
                        assertThat(threadPoolExecutor.getCompletedTaskCount()).isEqualTo(tasks);
                    });
        } finally {
            assertThat(MoreExecutors.shutdownAndAwaitTermination(delegate, Duration.ofSeconds(1)))
                    .as("Delegate failed to stop")
                    .isTrue();
        }
    }

    private record ThreadPeak(int peak, String description) {}

    /**
     * The maximum number of threads that were running at once. A thread is counted as running if it has a start
     * instant but not yet an end instant.
     */
    private static ThreadPeak peakThreads(List<Instant> starts, List<Instant> ends) {
        // direction is +1 for starts or -1 for ends
        record Tick(Instant instant, int direction) {}

        List<Tick> ticks = Streams.concat(
                        starts.stream().map(instant -> new Tick(instant, 1)),
                        ends.stream().map(instant -> new Tick(instant, -1)))
                .sorted(Comparator.comparing(Tick::instant))
                .toList();

        int running = 0;
        int max = 0;
        for (Tick tick : ticks) {
            running += tick.direction;
            max = Math.max(max, running);
        }
        return new ThreadPeak(max, ticks.toString());
    }
}
