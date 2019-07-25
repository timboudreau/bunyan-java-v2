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

import static com.mastfrog.bunyan.java.v2.LoggingConfig.PROP_AUTO_CONFIG_THRESHOLD;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.PROP_LOGGING_CONFIG_FILE;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.preconditions.ConfigurationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
class DelayedDelegationLogs implements Logs {

    static Set<DelayedDelegationLogs> INSTANCES = CollectionUtils.weakSet();
    static LoggingConfig config;
    final String name;
    private volatile Logs delegate;
    private Set<PreConfigCachingLog> pending = new CopyOnWriteArraySet<>();
    private static volatile int pendingLogCount;
    private static final int DEFAULT_INIT_THRESHOLD;

    static {
        boolean configFileSpecified = System.getProperty(PROP_LOGGING_CONFIG_FILE) != null
                || Boolean.getBoolean("bunyan.init.from.system.properties");
        if (configFileSpecified) {
            DEFAULT_INIT_THRESHOLD = 1;
        } else {
            int val = 20;
            String prop = System.getProperty(PROP_AUTO_CONFIG_THRESHOLD);
            if (prop == null) {
                prop = System.getenv("BUNYAN_AUTO_CONFIG_THRESHOLD");
            }
            if (prop != null) {
                try {
                    val = Math.max(1, Integer.parseInt(prop));
                } catch (NumberFormatException ex) {
                    LoggingLogging.log(true, PROP_AUTO_CONFIG_THRESHOLD
                            + " is not a number: '" + prop + "'");
                    ex.printStackTrace(System.err);
                }
            }
            DEFAULT_INIT_THRESHOLD = val;
        }
    }

    DelayedDelegationLogs(String name) {
        this.name = name;
        synchronized (DelayedDelegationLogs.class) {
            if (config == null) {
                INSTANCES.add(this);
            } else {
                delegate = config.logs(name);
            }
        }
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    static void onConfigShutdown(LoggingConfig config) {
        if (DelayedDelegationLogs.config == config) {
            DelayedDelegationLogs.config = null;
            LoggingLogging.log("Shutting down {0} and de-configuring {1} Logs instances",
                    config, INSTANCES.size());
            for (DelayedDelegationLogs logs : INSTANCES) {
                logs.deinit();
            }
        }
    }

    static Logs create(String name) {
        LoggingConfig cfig = config;
        if (config == null) {
            return new DelayedDelegationLogs(name);
        } else {
            return cfig.logs(name);
        }
    }

    private static void incrementPendingCount() {
        if (pendingLogCount++ == DEFAULT_INIT_THRESHOLD) {
            if (Boolean.getBoolean("bunyan.init.from.system.properties")) {
                LoggingLogging.log(true, DEFAULT_INIT_THRESHOLD + " pending logs, and"
                        + " no LoggingConfig has been constructed.  Assuming"
                        + " default configuration should be used and initializing"
                        + " from system properties.  To avoid this, create a"
                        + " LoggingConfig early in startup.");
            }
            try {
                LoggingConfig newConfig = LoggingConfig.fromSystemProperties();
                newConfig.logQueue().run(() -> {
                    setGlobalLoggingConfig(newConfig, false);
                });
            } catch (ConfigurationError err) {
                err.printStackTrace(System.err);
                LoggingLogging.log(true, "System-property based logging config is invalid.  Using console.");
                LoggingConfig fallback = LoggingConfig.builder().build();
                fallback.logQueue().run(() -> {
                    setGlobalLoggingConfig(fallback, false);
                });
            }
        } else if (pendingLogCount > DEFAULT_INIT_THRESHOLD) {
            // Ensure if any race between config being set
            // and detecting the delegate and config fields being set
            // is dealt with
            doubleCheckPendingInStaticInstances();
        }
    }

    private void init(LoggingConfig config) {
        delegate = config.logs(name);
        dumpPendingLogs();
    }

    synchronized void deinit() {
        delegate = null;
    }

    private synchronized void dumpPendingLogs() {
        if (delegate == null) {
            LoggingLogging.log("Tried to dump pending logs, but "
                    + "delegate was cleared before I could.");
            return;
        }
        List<PreConfigCachingLog> dumped = new ArrayList<>();
        outer:
        for (int loop = 0; !pending.isEmpty(); loop++) {
            LoggingLogging.log("Dump {0} logs for {1} to real logger loop {2}", pending.size(), name, loop++);
            for (PreConfigCachingLog log : pending) {
                if (delegate == null) {
                    LoggingLogging.log("Delegate removed while iterating");
                    break;
                }
                try (Log real = newLog(log.level)) {
                    if (real instanceof PreConfigCachingLog) {
                        break outer;
                    }
                    log.dumpInto(real);
                    dumped.add(log);
                }
            }
            pending.removeAll(dumped);
            dumped.clear();
        }
        // If we broke the loop, there will still be contents
        pending.removeAll(dumped);
    }

    static void doubleCheckPendingInStaticInstances() {
        if (config != null) {
            for (DelayedDelegationLogs logs : INSTANCES) {
                logs.doubleCheckPending();
            }
        }
    }

    void doubleCheckPending() {
        if (delegate != null && !pending.isEmpty()) {
            dumpPendingLogs();
        }
    }

    static void setGlobalLoggingConfig(LoggingConfig config, boolean force) {
        LoggingLogging.log("Attempt {0} initialization of global logging from {1}",
                (force ? "forced" : "unforced"), config);
        boolean init = false;
        synchronized (DelayedDelegationLogs.class) {
            LoggingConfig existingConfig = DelayedDelegationLogs.config;
            if (init = (config != existingConfig && (existingConfig == null || force))) {
                if (existingConfig != null) {
                    LoggingLogging.log("Shutting down existing config {0}", existingConfig);
                    existingConfig.shutdown();
                }
                DelayedDelegationLogs.config = config;
                LoggingLogging.log("Really changed global logging config.");
            } else {
                LoggingLogging.log("Not changing global logging config from {0}", existingConfig);
            }
        }
        if (init) {
            initAll(config);
        }
    }

    private static void initAll(LoggingConfig config) {
        for (DelayedDelegationLogs log : INSTANCES) {
            log.init(config);
        }
    }

    Logs delegate() {
        return delegate;
    }

    Log newLog(LogLevel level) {
        if (delegate == null) {
            return new PreConfigCachingLog(level);
        } else {
            switch (level.intValue()) {
                case LogLevel.DEBUG_LEVEL:
                    return delegate.debug();
                case LogLevel.TRACE_LEVEL:
                    return delegate.trace();
                case LogLevel.INFO_LEVEL:
                    return delegate.info();
                case LogLevel.WARN_LEVEL:
                    return delegate.warn();
                case LogLevel.ERROR_LEVEL:
                    return delegate.error();
                case LogLevel.FATAL_LEVEL:
                default:
                    return delegate.fatal();
            }
        }
    }

    static class ChildDelegatingLogs extends DelayedDelegationLogs {

        private final Map<String, Object> pairs;

        public ChildDelegatingLogs(String name, Map<String, Object> pairs) {
            super(name);
            this.pairs = pairs;
        }

        @Override
        Log newLog(LogLevel level) {
            Log result = super.newLog(level);
            result.add(pairs);
            return result;
        }
    }

    @Override
    public Logs child(Map<String, Object> pairs) {
        if (delegate != null) {
            return delegate.child(pairs);
        }
        return new ChildDelegatingLogs(name, pairs);
    }

    @Override
    public Log trace() {
        return newLog(LogLevel.TRACE);
    }

    @Override
    public Log debug() {
        return newLog(LogLevel.DEBUG);
    }

    @Override
    public Log info() {
        return newLog(LogLevel.INFO);
    }

    @Override
    public Log warn() {
        return newLog(LogLevel.WARN);
    }

    @Override
    public Log fatal() {
        return newLog(LogLevel.FATAL);
    }

    @Override
    public Log error() {
        return newLog(LogLevel.ERROR);
    }

    static boolean hasDelegate(Log log) { // for tests
        if (log instanceof LogImpl) {
            return true;
        } else if (log instanceof PreConfigCachingLog) {
            return ((PreConfigCachingLog) log).hasDelegate();
        } else {
            throw new IllegalArgumentException("I don't know what "
                    + log.getClass() + " is.");
        }
    }

    static boolean hasDelegate(Logs logs) {
        if (logs instanceof DelayedDelegationLogs) {
            return ((DelayedDelegationLogs) logs).delegate != null;
        }
        return true;
    }

    class PreConfigCachingLog implements Log {

        private final LogLevel level;
        private final List<Object> components = new ArrayList<>(3);
        private final String oldCtx;

        public PreConfigCachingLog(LogLevel level) {
            this.level = level;
            oldCtx = LogContext.enter(name);
        }

        boolean hasDelegate() {
            return delegate != null;
        }

        @Override
        public Log message(String msg) {
            components.add(msg);
            return this;
        }

        @Override
        public Log add(Object o) {
            components.add(o);
            return this;
        }

        @Override
        public Log add(String name, Object value) {
            components.add(AbstractSingletonMap.ofObject(name, value));
            return this;
        }

        @Override
        public Log add(String name, int value) {
            components.add(AbstractSingletonMap.ofInt(name, value));
            return this;
        }

        @Override
        public Log add(String name, long value) {
            components.add(AbstractSingletonMap.ofLong(name, value));
            return this;
        }

        @Override
        public Log add(String name, boolean value) {
            components.add(AbstractSingletonMap.ofBoolean(name, value));
            return this;
        }

        @Override
        public Log addLazy(String name, Supplier<Object> value) {
            components.add(AbstractSingletonMap.lazy(name, value));
            return this;
        }

        @Override
        public Log addIfNotNull(String name, Object value) {
            if (value != null) {
                components.add(AbstractSingletonMap.ofObject(name, value));
            }
            return this;
        }

        @Override
        public Log add(Throwable t) {
            components.add(t);
            return this;
        }

        void dumpInto(Log real) {
            for (Object o : components) {
                real.add(o);
            }
            components.clear();
        }

        void dump() {
            Log real = newLog(level);
            dumpInto(real);
            real.close();
        }

        @Override
        public void close() {
            LogContext.exitTo(oldCtx);
            if (delegate != null) {
                dump();
            } else {
                incrementPendingCount();
                if (delegate != null) {
                    dump();
                } else {
                    pending.add(this);
                }
            }
        }
    }
}
