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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
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
}
