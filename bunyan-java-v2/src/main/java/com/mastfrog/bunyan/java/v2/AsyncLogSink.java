/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
