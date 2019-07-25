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

import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Specialized singleton map implementations, since we use a LOT of these and
 * saving a few bytes of memory over the Collections.singletonMap()
 * implementation is worth it.
 *
 * @author Tim Boudreau
 */
abstract class AbstractSingletonMap extends AbstractMap<String, Object> implements Map.Entry<String, Object> {

    private final String key;
    private Set<Map.Entry<String, Object>> es;

    public static Map<String, Object> lazy(String key, Supplier<?> supp) {
        return new OfSupplier(supp, key);
    }

    public static Map<String, Object> ofObject(String key, Object ob) {
        return ob == null ? new OfNull(key) : new OfObject(ob, key);
    }

    public static Map<String, Object> ofInt(String key, int value) {
        return value == 0 ? new OfZero(key) : new OfInt(value, key);
    }

    public static Map<String, Object> ofLong(String key, long value) {
        return value == 0L ? new OfZero(key)
                : value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE
                        ? new OfInt((int) value, key)
                        : new OfLong(value, key);
    }

    public static Map<String, Object> ofBoolean(String key, boolean value) {
        return value ? new OfTrue(key) : new OfFalse(key);
    }

    String key() {
        return key;
    }

    static class OfSupplier extends AbstractSingletonMap {

        private final Supplier<?> supplier;

        public OfSupplier(Supplier<?> supplier, String key) {
            super(key);
            this.supplier = supplier;
        }

        @Override
        public Object getValue() {
            return supplier.get();
        }
    }

    static class OfObject extends AbstractSingletonMap {

        private final Object value;

        public OfObject(Object value, String key) {
            super(key);
            this.value = value;
        }

        @Override
        public Object getValue() {
            return value;
        }
    }

    static class OfNull extends AbstractSingletonMap {

        public OfNull(String key) {
            super(key);
        }

        @Override
        public Object getValue() {
            return null;
        }
    }

    static class OfInt extends AbstractSingletonMap {

        private final int value;

        OfInt(int value, String key) {
            super(key);
            this.value = value;
        }

        @Override
        public Object getValue() {
            return value;
        }
    }

    static class OfZero extends AbstractSingletonMap {

        static Integer ZERO = Integer.valueOf(0);

        OfZero(String key) {
            super(key);
        }

        @Override
        public Object getValue() {
            return ZERO;
        }
    }

    static class OfLong extends AbstractSingletonMap {

        private final long value;

        OfLong(long value, String key) {
            super(key);
            this.value = value;
        }

        @Override
        public Object getValue() {
            return value;
        }
    }

    static class OfTrue extends AbstractSingletonMap {

        static Boolean TRUE = Boolean.valueOf(true);

        public OfTrue(String key) {
            super(key);
        }

        @Override
        public Object getValue() {
            // autoboxing also has a cost
            return TRUE;
        }
    }

    static class OfFalse extends AbstractSingletonMap {

        static Boolean FALSE = Boolean.valueOf(false);

        public OfFalse(String key) {
            super(key);
        }

        @Override
        public Object getValue() {
            // autoboxing also has a cost
            return FALSE;
        }
    }

    AbstractSingletonMap(String key) {
        this.key = notNull("key", key);
    }

    @Override
    public Set<String> keySet() {
        return Collections.singleton(key);
    }

    @Override
    public Collection<Object> values() {
        return Collections.singleton(getValue());
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return es == null ? es = new ES() : es;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Object get(Object key) {
        return Objects.equals(key, this.key) ? getValue() : null;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public abstract Object getValue();

    @Override
    public Object setValue(Object value) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super Object> action) {
        action.accept(key, getValue());
    }

    @Override
    public boolean containsKey(Object key) {
        return Objects.equals(this.key, key);
    }

    @Override
    public boolean containsValue(Object value) {
        return Objects.equals(value, getValue());
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        return containsKey(key) ? getValue() : defaultValue;
    }

    class ES implements Set<Map.Entry<String, Object>> {

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean contains(Object o) {
            return Objects.equals(key, o);
        }

        @Override
        public Iterator<Entry<String, Object>> iterator() {
            return CollectionUtils.singletonIterator(AbstractSingletonMap.this);
        }

        @Override
        public Object[] toArray() {
            return new Object[]{AbstractSingletonMap.this};
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            return (T[]) toArray();
        }

        @Override
        public boolean add(Entry<String, Object> e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return c.size() == 1 && Objects.equals(c.iterator().next(), AbstractSingletonMap.this);
        }

        @Override
        public boolean addAll(Collection<? extends Entry<String, Object>> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }
    }
}
