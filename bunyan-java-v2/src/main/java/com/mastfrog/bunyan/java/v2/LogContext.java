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

/**
 *
 * @author Tim Boudreau
 */
final class LogContext {

    public static final String DEFAULT_CONTEXT = "default";

    private static final ThreadLocal<String> CURRENT_LOGGER = ThreadLocal.withInitial(() -> DEFAULT_CONTEXT);

    @SuppressWarnings("StringEquality")
    static boolean isDefaultContext() {
        return DEFAULT_CONTEXT == CURRENT_LOGGER.get();
    }

    static String current() {
        return CURRENT_LOGGER.get();
    }

    static String enter(String s) {
        String old = CURRENT_LOGGER.get();
        CURRENT_LOGGER.set(s);
        return old;
    }

    static String exitTo(String replaceWith) {
        return enter(replaceWith);
    }
}
