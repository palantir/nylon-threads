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

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 4, time = 3)
@Fork(1)
@Threads(1)
@State(Scope.Benchmark)
@SuppressWarnings("designforextension")
public class ThreadNamesBenchmark {

    @Benchmark
    public void threadSetName() {
        Thread.currentThread().setName(randomName());
    }

    @Benchmark
    public void nylon() {
        ThreadNames.setThreadName(Thread.currentThread(), randomName());
    }

    private static String randomName() {
        return "thread-" + ThreadLocalRandom.current().nextInt();
    }

    public static void main(String[] _args) throws Exception {
        Options options = new OptionsBuilder()
                .include(ThreadNamesBenchmark.class.getSimpleName())
                .build();
        new Runner(options).run();
    }
}
