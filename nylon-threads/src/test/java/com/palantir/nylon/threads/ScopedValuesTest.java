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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.nylon.threads.ScopedValues.ScopedValueSupport;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScopedValuesTest {

    @Test
    void testNewInstance() {
        Optional<ScopedValueSupport<Integer>> value = ScopedValues.newInstance();
        assertThat(value).isPresent();
    }

    @Test
    void testIsBound() {
        Optional<ScopedValueSupport<Integer>> maybeValue = ScopedValues.newInstance();
        assertThat(maybeValue).isPresent();

        ScopedValueSupport<Integer> value = maybeValue.get();
        assertThat(value.isBound()).isFalse();

        ScopedValues.runWhere(value, 123, () -> {
            assertThat(value.isBound()).isTrue();
            assertThat(value.get()).isEqualTo(123);
        });
    }

    @Test
    void testOrElse() {
        Optional<ScopedValueSupport<Integer>> maybeValue = ScopedValues.newInstance();
        assertThat(maybeValue).isPresent();

        ScopedValueSupport<Integer> value = maybeValue.get();
        ScopedValues.runWhere(value, 123, () -> {
            assertThat(value.orElse(999)).isEqualTo(123);
        });

        assertThat(value.orElse(999)).isEqualTo(999);
    }

    @Test
    void testOrElseThrow() {
        Optional<ScopedValueSupport<Integer>> maybeValue = ScopedValues.newInstance();
        assertThat(maybeValue).isPresent();

        ScopedValueSupport<Integer> value = maybeValue.get();
        ScopedValues.runWhere(value, 123, () -> {
            assertThat(value.orElseThrow(() -> new RuntimeException("foo"))).isEqualTo(123);
        });

        assertThatThrownBy(() -> value.orElseThrow(() -> new RuntimeException("foo")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("foo");
    }

    @Test
    void testRunWhere() {
        Optional<ScopedValueSupport<Integer>> maybeValue = ScopedValues.newInstance();
        assertThat(maybeValue).isPresent();

        ScopedValueSupport<Integer> value = maybeValue.get();
        ScopedValues.runWhere(value, 123, () -> assertThat(value.get()).isEqualTo(123));
    }

    @Test
    void testRunWhereRebinding() {
        Optional<ScopedValueSupport<Integer>> maybeValue = ScopedValues.newInstance();
        assertThat(maybeValue).isPresent();

        ScopedValueSupport<Integer> value = maybeValue.get();
        ScopedValues.runWhere(value, 1, () -> {
            assertThat(value.get()).isEqualTo(1);

            ScopedValues.runWhere(value, 999, () -> assertThat(value.get()).isEqualTo(999));

            assertThat(value.get()).isEqualTo(1);
        });
    }

    @Test
    void testCallWhere() {
        Optional<ScopedValueSupport<Integer>> maybeValue = ScopedValues.newInstance();
        assertThat(maybeValue).isPresent();

        ScopedValueSupport<Integer> value = maybeValue.get();
        int result = ScopedValues.callWhere(value, 1, () -> value.get() + 2);
        assertThat(result).isEqualTo(3);
    }

    @Test
    void testCallWhereRebinding() {
        Optional<ScopedValueSupport<Integer>> maybeValue = ScopedValues.newInstance();
        assertThat(maybeValue).isPresent();

        ScopedValueSupport<Integer> value = maybeValue.get();
        int result = ScopedValues.callWhere(value, 1, () -> {
            int resultInner = ScopedValues.callWhere(value, 100, () -> value.get() + 200);
            assertThat(resultInner).isEqualTo(300);
            return value.get() + 2;
        });
        assertThat(result).isEqualTo(3);
    }

    @Test
    void testGetWhere() {
        Optional<ScopedValueSupport<Integer>> maybeValue = ScopedValues.newInstance();
        assertThat(maybeValue).isPresent();

        ScopedValueSupport<Integer> value = maybeValue.get();
        int result = ScopedValues.getWhere(value, 123, value::get);
        assertThat(result).isEqualTo(123);
    }

    @Test
    void testWhereGetScopedValue() {
        Optional<ScopedValueSupport<Integer>> maybeValue = ScopedValues.newInstance();
        assertThat(maybeValue).isPresent();

        ScopedValueSupport<Integer> value = maybeValue.get();
        int result = ScopedValues.where(value, 100).get(value);
        assertThat(result).isEqualTo(100);
    }

    @Test
    void testWhereGetSupplier() {
        Optional<ScopedValueSupport<Integer>> maybeValue = ScopedValues.newInstance();
        assertThat(maybeValue).isPresent();

        ScopedValueSupport<Integer> value = maybeValue.get();
        int result = ScopedValues.where(value, 99).get(() -> value.get() + 1);
        assertThat(result).isEqualTo(100);
    }

    @Test
    void testWhereRun() {
        Optional<ScopedValueSupport<Integer>> maybeValue = ScopedValues.newInstance();
        assertThat(maybeValue).isPresent();

        ScopedValueSupport<Integer> value = maybeValue.get();
        ScopedValues.where(value, 123).run(() -> {
            assertThat(value.get()).isEqualTo(123);
        });
    }
}
