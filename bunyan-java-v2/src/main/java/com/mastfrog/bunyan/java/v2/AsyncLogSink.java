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

import com.fasterxml.jackson.annotation.JsonValue;
import com.mastfrog.abstractions.Wrapper;
import java.util.Map;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
class AsyncLogSink implements LogSink, Wrapper<LogSink> {

    private final Supplier<LoggingConfig> config;

    final LogSink orig;

    AsyncLogSink(LoggingConfig config, LogSink orig) {
        this(() -> config, orig);
    }

    AsyncLogSink(Supplier<LoggingConfig> config, LogSink orig) {
        this.config = config;
        this.orig = orig;
    }

    @JsonValue
    LogSink original() {
        return orig;
    }

    @Override
    public String toString() {
        return "async(" + orig + ")";
    }

    static LogSink unwrap(LogSink sink) {
        if (sink instanceof AsyncLogSink) {
            return ((AsyncLogSink) sink).orig;
        }
        return sink;
    }

    @Override
    public LogSink and(LogSink other) {

        if (other instanceof AsyncLogSink) {
            other = ((AsyncLogSink) other).orig;
        }
        return new AsyncLogSink(config, orig.and(other));
    }

    @Override
    public void push(JSONContext ctx, Map<String, Object> logrecord) {
        LoggingConfig config = null;
        if (orig instanceof AbstractLogSink) {
            config = ((AbstractLogSink) orig).configSupplier().get();
            if (config == null) {
                config = DelayedDelegationLogs.config;
            }
        } else {
            config = DelayedDelegationLogs.config;
        }
        config.logQueue().enqueue(orig, ctx, logrecord);
    }

    @Override
    public LogSink wrapped() {
        return orig;
    }
}
