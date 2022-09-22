/*
 * The MIT License
 *
 * Copyright 2019 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.bunyan.java.v2;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
final class AtomicConversionMap<T> extends AbstractMap<String, T> {

    private final Set<AtomicEntry<T>> allEntries = new HashSet<>();
    private final Map<String, AtomicEntry<T>> entryCache = new HashMap<>();

    <O> AtomicConversionMap(Map<String, O> source, Function<O, T> creator) {
        for (Map.Entry<String, O> e : source.entrySet()) {
            AtomicEntry<T> ae = new AtomicEntry<>(e.getKey(), () -> {
                return creator.apply(e.getValue());
            });
            allEntries.add(ae);
            entryCache.put(e.getKey(), ae);
        }
    }

    static <O, T> Map<String, T> create(Map<String, O> source, Function<O, T> factory) {
        return new AtomicConversionMap<>(source, factory);
    }

    @Override
    public T get(Object key) {
        AtomicEntry<T> result = entryCache.get(key);
        return result == null ? null : result.getValue();
    }

    @Override
    public int size() {
        return entryCache.size();
    }

    public boolean containsKey(String key) {
        return entryCache.containsKey(key);
    }

    @Override
    public Set<Entry<String, T>> entrySet() {
        Set<Entry<String, T>> result = new HashSet<>();
        for (AtomicEntry<T> e : allEntries) {
            if (e.hasValue()) {
                result.add(e);
            }
        }
        return result;
    }

    static final class AtomicEntry<T> implements Map.Entry<String, T> {

        private final String key;
        private final Supplier<T> valueSupplier;
        private AtomicReference<T> value = new AtomicReference<>();

        public AtomicEntry(String key, Supplier<T> valueSupplier) {
            this.key = key;
            this.valueSupplier = valueSupplier;
        }

        public boolean hasValue() {
            return value.get() != null;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public T getValue() {
            return value.updateAndGet(old -> {
                if (old != null) {
                    return old;
                }
                return valueSupplier.get();
            });
        }

        @Override
        public T setValue(T value) {
            throw new UnsupportedOperationException("Not supported.");
        }
    }
}
