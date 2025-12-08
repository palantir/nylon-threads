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
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** Utility functionality to rename {@link Thread threads} as efficiently as possible. */
public final class ThreadNames {

    private static final VarHandle THREAD_NAME = getThreadNameVarHandle();

    /**
     * Sets the name of a java thread without native overhead to rename the OS thread.
     * Note that unlike {@link Thread#setName(String)} this implementation may not
     * synchronize on the {@code thread} object.
     */
    public static void setThreadName(Thread thread, String name) {
        Preconditions.checkNotNull(thread, "Thread is required");
        Preconditions.checkNotNull(name, "Thread name is required");
        THREAD_NAME.setVolatile(thread, name);
    }

    private static VarHandle getThreadNameVarHandle() {
        try {
            return MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup())
                    .findVarHandle(Thread.class, "name", String.class);
        } catch (ReflectiveOperationException e) {
            throw new SafeIllegalStateException("Failed to load thread name var handle", e);
        }
    }

    private ThreadNames() {}
}
