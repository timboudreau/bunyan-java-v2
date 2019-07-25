/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
