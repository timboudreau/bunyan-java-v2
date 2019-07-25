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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.collections.CollectionUtils;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
final class LogRecordRouter implements BiFunction<String, LogLevel, LogSink> {

    @JsonProperty("minLogLevelForLogger")
    private final Map<String, LogLevel> minLogLevelForLogger;
    @JsonProperty("logFileForLogger")
    private final Map<String, LogSink> logFileForLogger;
    @JsonProperty("defaultRoute")
    private final LogSink defaultSink;
    @JsonProperty("logSinkForLogger")
    private final Map<String, LogSink> logSinkForLogName;
    @JsonProperty("logSinkForSevere")
    private final LogSink logSinkForSevere;
    @JsonProperty("logPathForSevere")
    private final Path logPathForSevere;
    private volatile LogSink severe;
    private final ThrowingRunnable onShutdown;
    @JsonProperty("async")
    private final boolean async;
    @JsonProperty("liveSinks")
    private final Map<String, LogSink> liveSinks = new ConcurrentHashMap<>();
    private final Supplier<LoggingConfig> configSupplier;
    @JsonProperty("routes")
    private final Map<String, Path> pathForLogger;

    public LogRecordRouter(
            Map<String, LogLevel> minLogLevelForLogger,
            Map<String, Path> pathForLogger, LogSink defaultSink,
            boolean async, ThrowingRunnable onShutdown,
            Map<String, LogSink> logSinkForLogName,
            Path logPathForSevere, LogSink logSinkForSevere,
            Supplier<LoggingConfig> configSupplier) {
        this.minLogLevelForLogger = minLogLevelForLogger;
        this.defaultSink = defaultSink;
        this.onShutdown = onShutdown;
        this.configSupplier = configSupplier;
        this.logSinkForLogName = copyWithAsync(logSinkForLogName, async);
        this.logPathForSevere = logPathForSevere;
        this.logSinkForSevere = logSinkForSevere;
        this.pathForLogger = pathForLogger;
        this.async = async;
        logFileForLogger = AtomicConversionMap.create(pathForLogger, (p) -> {
            LogSink result = new FileLogSink(p, configSupplier);
            onShutdown.andAlways((FileLogSink) result);
            if (async) {
                result = configSupplier.get().toAsyncLogSink(result);
            }
            return result;
        });
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(100);
        sb.append("async: ").append(async)
                .append(", default: ").append(defaultSink)
                .append(", severe: ").append(logPathForSevere).append(" / ").append(logSinkForSevere)
                .append(", levels: ").append(CollectionUtils.invert(minLogLevelForLogger))
                .append(", paths: ").append(CollectionUtils.invert(pathForLogger))
                .append(", sinks: ").append(CollectionUtils.invert(logSinkForLogName));
        ;
        return sb.toString();
    }

    private Map<String, LogSink> copyWithAsync(Map<String, LogSink> m, boolean async) {
        Map<String, LogSink> copy = new HashMap<>();
        String[] keys = new String[m.size()];
        int ix = 0;
        for (Map.Entry<String, LogSink> e : m.entrySet()) {
            // XXX do we need this, or just wrap everything in one mongo
            // async log sink?  Or have THIS implement LogSink and return that?
            if (async) {
                copy.put(e.getKey(), new LazyAsyncLogSink(e.getValue()));
            } else {
                copy.put(e.getKey(), e.getValue());
            }
            keys[ix++] = e.getKey();
        }
        if (true) {
            return copy;
        }
        Arrays.sort(keys);
        return CollectionUtils.immutableArrayMap(m, String.class, LogSink.class,
                (Object obj) -> Arrays.binarySearch(keys, obj));
    }

    private final class LazyAsyncLogSink implements LogSink {

        private final AtomicReference<LogSink> sink = new AtomicReference<>();
        @JsonValue
        private final LogSink orig;

        public LazyAsyncLogSink(LogSink orig) {
            this.orig = orig;
        }

        private LogSink sink() {
            LogSink result = sink.get();
            if (result == null) {
                sink.compareAndSet(null, configSupplier.get().toAsyncLogSink(orig));
                result = sink.get();
            }
            return result;
        }

        @Override
        public void push(JSONContext ctx, Map<String, Object> logrecord) {
            sink().push(ctx, logrecord);
        }
    }

    private LogSink severe() {
        LogSink sev = severe;
        if (sev != null) {
            return sev;
        } else if (logSinkForSevere != null || logPathForSevere != null) {
            synchronized (this) {
                sev = severe;
                if (sev != null) {
                    return sev;
                }
                if (logSinkForSevere != null) {
                    sev = logSinkForSevere;
                }
                if (logPathForSevere != null) {
                    FileLogSink sink = new FileLogSink(logPathForSevere, configSupplier);
                    onShutdown.andAlways(sink);
                    if (sev == null) {
                        sev = sink;
                    } else {
                        sev = sink.and(sev);
                    }
                }
                return severe = async ? configSupplier.get().toAsyncLogSink(sev) : sev;
            }
        }
        return null;
    }

    private LogSink applySevere(String t, LogLevel lev) {
        if (!lev.isSevere()) {
            return LogSink.NULL;
        }
        LogSink sev = severe();
        if (sev != null && async) {
            sev = configSupplier.get().toAsyncLogSink(sev);
        }
        return sev == null ? LogSink.NULL : sev;
    }

    @Override
    public LogSink apply(String t, LogLevel u) {
        LogSink alsoSevere = applySevere(t, u);
        LogLevel min = minLogLevelForLogger.get(t);
        if (u.intValue() < min.intValue()) {
            return alsoSevere;
        }
        LogSink result = liveSinks.get(t + u.intValue());
        if (result == null) {
            result = liveSinks.get(t);
            if (result == null) {
                LogSink fb = logSinkForLogName.get(t);
                if (fb != null) {
                    LogSink fileSink = logFileForLogger.get(t);
                    if (fileSink != null) {
                        result = fileSink.and(fb);
                    } else {
                        result = fb;
                    }
                } else {
                    result = logFileForLogger.getOrDefault(t, defaultSink);
                }
//                LogSink sev = severe();
//                if (sev != null) {
//                    result = sev.and(result);
//                }
                if (async) {
                    result = configSupplier.get().toAsyncLogSink(result);
                }
                liveSinks.put(t + u.intValue(), result);
            }
        }
        return alsoSevere.and(result);
    }
}
