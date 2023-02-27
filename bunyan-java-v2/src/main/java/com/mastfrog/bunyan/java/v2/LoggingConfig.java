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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import static com.mastfrog.bunyan.java.v2.LogLevel.DEBUG_LEVEL;
import static com.mastfrog.bunyan.java.v2.LogLevel.ERROR_LEVEL;
import static com.mastfrog.bunyan.java.v2.LogLevel.FATAL_LEVEL;
import static com.mastfrog.bunyan.java.v2.LogLevel.INFO_LEVEL;
import static com.mastfrog.bunyan.java.v2.LogLevel.TRACE_LEVEL;
import static com.mastfrog.bunyan.java.v2.LogLevel.WARN_LEVEL;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.DefaultLoggingConfigHandling.NON_DEFAULT;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.DefaultLoggingConfigHandling.SET_IF_UNSET;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.DefaultLoggingConfigHandling.TAKE_OVER;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.JsonSerializationPolicy.ADAPTIVE;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.JsonSerializationPolicy.NEVER_JACKSON;
import com.mastfrog.function.LoggableConsumer;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.map;
import static com.mastfrog.util.preconditions.Checks.greaterThanZero;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.io.InputStream;
import static java.lang.Math.max;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Configuration for bunyan-v2 logging. Implements AutoClosable for shutdown.
 *
 * @author Tim Boudreau
 */
public final class LoggingConfig implements AutoCloseable {

    /**
     * For use with <code>LoggingConfig.fromSystemProperties()</code> or
     * <code>LoggingConfig.fromProperties(Properties)</code>: If "true", logging
     * will be asynchronous and not block the caller.
     */
    public static final String PROP_ASYNC = "bunyan-v2-log-async";
    /**
     * For use with <code>LoggingConfig.fromSystemProperties()</code> or
     * <code>LoggingConfig.fromProperties(Properties)</code>: If using
     * asynchronous logging, the number of threads to use for asynchronous
     * logging. If logging to many separate files frequently, a good choice is
     * some number > numFiles / 2 but less than numFiles.
     */
    public static final String PROP_THREADS = "bunyan-v2-log-async-threads";
    /**
     * For use with <code>LoggingConfig.fromSystemProperties()</code> or
     * <code>LoggingConfig.fromProperties(Properties)</code>: The default log
     * file. If unset, logging defaults to the console.
     */
    public static final String PROP_DEFAULT_FILE = "bunyan-v2-default-log-file";
    /**
     * For use with <code>LoggingConfig.fromSystemProperties()</code> or
     * <code>LoggingConfig.fromProperties(Properties)</code>: If set, log errors
     * of severity "error" or "fatal" to this file in addition to the default
     * destination.
     */
    public static final String PROP_SEVERE_FILE = "bunyan-v2-severe-log-file";
    /**
     * For use with <code>LoggingConfig.fromSystemProperties()</code> or
     * <code>LoggingConfig.fromProperties(Properties)</code>: The minimum level
     * to log at.
     */
    public static final String PROP_MIN_LEVEL = "bunyan-v2-level";
    /**
     * For use with <code>LoggingConfig.fromSystemProperties()</code> or
     * <code>LoggingConfig.fromProperties(Properties)</code>: If "true", get the
     * current stack trace and find the first stack trace element outside this
     * library and the JDK and include information about it in the log record
     * (EXPENSIVE! not for production use).
     */
    public static final String PROP_LOG_CALLERS = "bunyan-v2-log-callers";
    /**
     * For use with <code>LoggingConfig.fromSystemProperties()</code> or
     * <code>LoggingConfig.fromProperties(Properties)</code>: If "true", include
     * an atomically incremented global sequence number in each log record with
     * the name "seq". This can be useful in the case of asynchronous logging,
     * which may reorder records, in order to determine the order of operations.
     * Note the sequence number is assigned at the time of a Log instance's
     * <i>closing</i>, which does not mean it was
     * <i>created</i> not created before a record that is subsequently closed.
     */
    public static final String PROP_SEQ_NUMBERS = "bunyan-v2-seq-numbers";
    /**
     * For use with <code>LoggingConfig.fromSystemProperties()</code> or
     * <code>LoggingConfig.fromProperties(Properties)</code>: If "true", log to
     * the console in addition to any file specified (be default, specifying a
     * file turns off console logging).
     */
    public static final String PROP_LOG_CONSOLE = "bunyan-v2-log-console";
    /**
     * For use with <code>LoggingConfig.fromSystemProperties()</code> or
     * <code>LoggingConfig.fromProperties(Properties)</code>: A comma-delimited
     * list of names of loggers which should be routed to other files. The
     * system will look for a property named, for example,
     * <code>bunyan-v2-route.downloader</code> for a logger named
     * <code>downloader</code>, and if that property is present, its value will
     * be used as a file path for records from that log namespace.
     */
    public static final String PROP_ROUTED_LOGGERS = "bunyan-v2-routed-loggers";
    /**
     * For use with <code>LoggingConfig.fromSystemProperties()</code> or
     * <code>LoggingConfig.fromProperties(Properties)</code>: Prefix for custom
     * properties which specify a different destination for specific loggers.
     */
    public static final String PROP_ROUTE_PREFIX = "bunyan-v2-route.";
    /**
     * For use with <code>LoggingConfig.fromSystemProperties()</code> or
     * <code>LoggingConfig.fromProperties(Properties)</code>: Prefix for custom
     * properties which specify a different minimum log level for specific
     * loggers.
     */
    public static final String PROP_ROUTE_LEVEL_PREFIX = "bunyan-v2-route-level.";

    /**
     * Property to use for the host name (the HOSTNAME environment variable is
     * also checked) in log records, before trying to find it via the Java
     * networking API (which can often return "localhost"). Used by
     * <code>LoggingConfig.fromProperties()</code> and
     * <code>LoggingConfig.fromSystemProperties()</code>>..
     */
    public static final String PROP_HOSTNAME = "hostname";

    /**
     * Property for how many pending log records need to exist with no logging
     * configuration set up, before initializing logging from system properties.
     * The default is 20. Used by <code>LoggingConfig.fromProperties()</code>
     * and <code>LoggingConfig.fromSystemProperties()</code>>..
     */
    public static final String PROP_AUTO_CONFIG_THRESHOLD = "bunyan-v2-autoconfig-threshold";

    /**
     * Property which, if set, will trigger loading from a configuration file on
     * startup. Used by <code>LoggingConfig.fromProperties()</code> and
     * <code>LoggingConfig.fromSystemProperties()</code>>..
     */
    public static final String PROP_LOGGING_CONFIG_FILE = "bunyan-v2-logging-config-file";

    /**
     * Sets the thread priority for async logging threads. Used by
     * <code>LoggingConfig.fromProperties()</code> and
     * <code>LoggingConfig.fromSystemProperties()</code>>..
     */
    public static final String PROP_ASYNC_LOGGING_THREAD_PRIORITY = "bunyan-v2-async-log-thread-priority";

    /**
     * Policy for how to serialize JSON - by default, we use Jackson some of the
     * time, and a less-intensive serializer when the contents of a log record
     * are simple types. If you are using custom JSON serialization, you may
     * want to always use Jackson, to ensure those properties do not vary (note
     * that timestamps <i>must</i> be ISO-8601 format to be compatible with
     * node-bunyan) in how they are serialized. The possible values are
     * <code>adaptive</code>, <code>always-jackson</code> or
     * <code>never-jackson</code>.
     */
    public static final String PROP_JSON_SERIALIZATION_POLICY = "bunyan-v4-json-policy";
    /**
     * Value for PROP_JSON_SERIALIZATION_POLICY to use the lightweight JSON
     * serializer when possible. Used by
     * <code>LoggingConfig.fromProperties()</code> and
     * <code>LoggingConfig.fromSystemProperties()</code>>..
     */
    public static final String PROP_VALUE_JSON_SERIALIZATION_POLICY_ADAPTIVE = "adaptive";
    /**
     * Value for PROP_JSON_SERIALIZATION_POLICY to always use Jackson for
     * consistency.
     */
    public static final String PROP_VALUE_JSON_SERIALIZATION_POLICY_ALWAYS_JACKSON = "always-jackson";
    /**
     * Value for PROP_JSON_SERIALIZATION_POLICY to always use the lightweight
     * serializer if you know you will not be encoding types that it cannot
     * serialize into log records. Primitives, maps, lists, arrays, and a wide
     * variety of common Java types such as Path, URL, InetAddress,
     * InetSocketAddress, Date, newer date and duration variants and more are
     * supported by the lightweight serializer.
     */
    public static final String PROP_VALUE_JSON_SERIALIZATION_POLICY_NEVER_JACKSON = "never-jackson";

    /**
     * Property for whether or not the logger should take over as the default
     * logging config used by static Logs instances, take over only if none has
     * been set, or not set itself as the default logging config at all.
     * Possible values are <code>non-default</code>, <code>set-if-unset</code>
     * and <code>take-over</code>. Note that if the system is self-configuring
     * from system properties, that is happening because no default
     * configuration was set, and so the value of this is always effectively
     * <code>set-if-unset</code>.
     */
    public static final String PROP_DEFAULT_CONFIG_POLICY = "bunyan-v2-logging-config-policy";

    public static final String PROP_VALUE_DONT_SET_AS_DEFAULT_CONFIG = "non-default";
    public static final String PROP_VALUE_USE_AS_DEFAULT_CONFIG_IF_UNSET = "set-if-unset";
    public static final String PROP_VALUE_TAKE_OVER_AS_DEFAULT_CONFIG = "take-over";

    public static final String PROP_ESCALATE_ON_ERROR = "bunyan-v2-logging-escalate-errors";
    public static final String PROP_USE_SHUTDOWN_HOOK = "bunyan-v2-shutdown-hook";

    public static final String PROP_LOG_ROTATION_MAX_SIZE_MB = "bunyan-v2-log-rotation-size-mb";

    private static ThreadLocal<LoggingConfig> TAKING_OVER = new ThreadLocal<>();

    @JsonProperty("minLevel")
    private final int minLevel;
    @JsonProperty("levelConfig")
    private final BiPredicate<String, LogLevel> levelConfig;
    @JsonProperty("router")
    private final BiFunction<String, LogLevel, LogSink> sinkRouter;
    private final ThrowingRunnable onShutdown;
    @JsonProperty("decorator")
    private final Consumer<Log> decorator;
    private final ObjectMapper _mapper;
    @JsonProperty("async")
    private final AsyncLogQueue logQueue;
    @JsonProperty("policy")
    private final JsonSerializationPolicy jsonPolicy;
    @JsonProperty("hostname")
    private final String hostname;
    @JsonProperty("escalateOnError")
    private final boolean escalateOnError;

    @SuppressWarnings("LeakingThisInConstructor")
    LoggingConfig(ObjectMapper mapper, int minLevel,
            BiPredicate<String, LogLevel> levelForLoggerName,
            BiFunction<String, LogLevel, LogSink> sinkForNameAndLevel,
            ThrowingRunnable onShutdown, Consumer<Log> decorator,
            boolean recordCaller, int asyncThreads,
            DefaultLoggingConfigHandling defaultHandling, int asyncThreadPriority,
            JsonSerializationPolicy jsonPolicy, String hostname,
            boolean escalateOnError, boolean useShutdownHook) {
        this._mapper = mapper.copy();
        this._mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS,
                SerializationFeature.FAIL_ON_SELF_REFERENCES,
                SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS);
        this.minLevel = minLevel;
        this.jsonPolicy = jsonPolicy;
        this.levelConfig = levelForLoggerName;
        this.sinkRouter = sinkForNameAndLevel;
        this.hostname = hostname;
        this.escalateOnError = escalateOnError;
        this.onShutdown = onShutdown;
        this.decorator = !recordCaller ? decorator
                : decorator == null ? new RecordCallerDecorator() : decorator.andThen(new RecordCallerDecorator());
        this.logQueue = new AsyncLogQueue(asyncThreads, asyncThreadPriority, useShutdownHook);
        if (useShutdownHook) {
            HookThread.add(this);
        }
        if (defaultHandling.isSetIt()) {
            LoggingConfig old = TAKING_OVER.get();
            TAKING_OVER.set(this);
            try {
                DelayedDelegationLogs.setGlobalLoggingConfig(this, defaultHandling.isForce());
            } finally {
                TAKING_OVER.set(old);
            }
        }
    }

    static final class HookThread extends Thread {

        private final Set<LoggingConfig> configs = CollectionUtils.weakSet();
        private static HookThread INSTANCE;

        HookThread() {
            setName("bunyan-v2-shutdown-hook");
            setDaemon(true);
            assert INSTANCE == null : "Created twice";
            INSTANCE = this;
            Runtime.getRuntime().addShutdownHook(this);
        }

        static void add(LoggingConfig cfig) {
            getDefault().configs.add(cfig);
        }

        static synchronized HookThread getDefault() {
            return INSTANCE == null ? INSTANCE = new HookThread() : INSTANCE;
        }

        @Override
        public void run() {
            for (LoggingConfig cfig : configs) {
                if (cfig != null) {
                    try {
                        cfig.shutdown();
                    } catch (Exception ex) {
                        LoggingLogging.log(ex);
                    }
                }
            }
        }
    }

    public boolean isEscalate() {
        return escalateOnError;
    }

    String jsonize() {
        // Used by some tests to ensure logging config is created correctly,
        // without having to directly expose internals to do that
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException ex) {
            LoggingLogging.log("Could not serialize", ex, true);
            return Exceptions.chuck(ex);
        }
    }

    String hostname() {
        return hostname;
    }

    JsonSerializationPolicy serializationPolicy() {
        return jsonPolicy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(80).append("LoggingConfig(minLevel: ")
                .append(LogLevel.valueOf(minLevel))
                .append(", levelConfig: ").append(levelConfig)
                .append(", sinkConfig:").append(sinkRouter)
                .append(", decorators: ").append(decorator);

        return sb.append(')').toString();
    }

    static final class RecordCallerDecorator implements LoggableConsumer<Log> {

        private static final String PACKAGE = LoggingConfig.class.getPackage().getName();

        @Override
        public void accept(Log t) {
            StackTraceElement[] el = Thread.currentThread().getStackTrace();
            if (el != null && el.length > 3) { // built without debug info
                for (int i = 3; i < el.length; i++) {
                    StackTraceElement e = el[i];
                    if (!e.getClassName().startsWith(PACKAGE) && !e.getClassName().startsWith("java")) {
                        t.add("caller", map("class").to(e.getClassName())
                                .map("file").to(e.getFileName()).map("line").to(e.getLineNumber())
                                .map("method").finallyTo(e.getMethodName()));
                        break;
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "record-callers";
        }
    }

    ObjectMapper _mapper() {
        return _mapper;
    }

    void decorate(Log log) {
        if (decorator != null) {
            decorator.accept(log);
        }
    }

    /**
     * Get a Logs instance for the specified name.
     *
     * @param name The name for log records created by the returned Logs
     * @return A log record factory
     */
    public final Logs logs(String name) {
        return new LogsImpl(notNull("name", name), this);
    }

    AsyncLogQueue logQueue() {
        return logQueue;
    }

    /**
     * Converts a log sink into a wrapper which uses the async thread pool
     * attached to this configuration.
     *
     * @param sink A log sink
     * @return An asynchronous log sink, or the passed one if it is already
     * asynchronous
     */
    public final LogSink toAsyncLogSink(LogSink sink) {
        if (sink instanceof AsyncLogSink || sink == LogSink.NULL) {
            return sink;
        }
        LogSink target = CombinedLogSink.deAsync(sink);
        return new AsyncLogSink(this, target);
    }

    /**
     * Shut down this logging configuration, de-initializing any static Logs
     * instances which were initialized by it and are still referenced,
     * switching any asynchronous loggers over to writing synchronously, and
     * waiting for all asynchronous logging threads to complete, and closing any
     * open file channels.
     */
    public final void shutdown() {
        try {
            logQueue.shutdown();
        } finally {
            onShutdown.toNonThrowing().run();
            DelayedDelegationLogs.onConfigShutdown(this);
        }
    }

    /**
     * Implementation of AutoCloseable delegating to shutdown().
     */
    @Override
    public void close() {
        shutdown();
    }

    public synchronized void onShutdown(ThrowingRunnable run) {
        onShutdown.andAlways(run);
    }

    JSONEncoder mapper() {
        return JSONEncoder.SIMPLE;
    }

    LogSink sinkFor(String loggerName, LogLevel level) {
        LogSink result;
        if (!isEnabled(loggerName, level)) {
            result = LogSink.NULL;
        } else {
            result = sinkRouter.apply(loggerName, level);
            if (result == null) {
                result = LogSink.NULL;
            }
        }
        return result;
    }

    boolean isEnabled(LogLevel level) {
        return minLevel <= level.intValue();
    }

    boolean isEnabled(String name, LogLevel level) {
        return levelConfig.test(name, level) || isEnabled(level);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static LoggingConfig fromSystemProperties() {
        String configFile = System.getProperty(PROP_LOGGING_CONFIG_FILE);
        if (configFile != null) {
            Path path = Paths.get(configFile);
            if (Files.exists(path)) {
                if (Files.isDirectory(path)) {
                    LoggingLogging.log(true, "Logging config file specified as system "
                            + "property '" + PROP_LOGGING_CONFIG_FILE + "' is a "
                            + "directory, not a file. Ignoring.");
                } else {
                    if (Files.isReadable(path)) {
                        Properties props = new Properties();
                        try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
                            props.load(in);
                        } catch (IOException ex) {
                            LoggingLogging.log("Exception loading config file specified in "
                                    + "system property '" + PROP_LOGGING_CONFIG_FILE
                                    + "', which is '" + path
                                    + "'.  Using defaults.", ex, true);
                        }
                        return fromProperties(props);
                    } else {
                        LoggingLogging.log(true, "Logging config file specified as system "
                                + "property '" + PROP_LOGGING_CONFIG_FILE + "' is not "
                                + "readable by this process. Ignoring.");

                    }
                }
            } else {
                LoggingLogging.log(true, "Logging config file specified as system "
                        + "property '" + PROP_LOGGING_CONFIG_FILE + "' does not "
                        + "actually exist. Ignoring.");
            }
        }
        return fromProperties(System.getProperties());
    }

    public static LoggingConfig fromProperties(Properties props) {
        Builder b = builder();
        if ("true".equals(props.getProperty(PROP_ASYNC))) {
            b.asyncLogging();
        }
        if (props.containsKey(PROP_THREADS)) {
            b.asyncLoggingThreads(Integer.parseInt(props.getProperty(PROP_THREADS)));
        }
        if (props.containsKey(PROP_DEFAULT_FILE)) {
            b.logToFile(Paths.get(props.getProperty(PROP_DEFAULT_FILE)));
        }
        if (props.containsKey(PROP_SEVERE_FILE)) {
            b.logErrorAndFatalTo(Paths.get(props.getProperty(PROP_SEVERE_FILE)));
        }
        if (props.containsKey(PROP_MIN_LEVEL)) {
            LogLevel level = LogLevel.valueOf(props.getProperty(PROP_MIN_LEVEL));
            b.dll.minimum = level.intValue();
        }
        if (props.containsKey(PROP_HOSTNAME)) {
            b.hostNameForLogRecords(props.getProperty(PROP_HOSTNAME));
        }
        if ("true".equals(props.getProperty(PROP_LOG_CALLERS))) {
            b.recordCaller();
        }
        if ("true".equals(props.getProperty(PROP_SEQ_NUMBERS))) {
            b.withSequenceNumbers();
        }
        if ("true".equals(props.getProperty(PROP_LOG_CONSOLE))) {
            b.logToConsole();
        }
        if ("true".equals(props.getProperty(PROP_ESCALATE_ON_ERROR))) {
            b.escalateOnError();
        } else if ("false".equals(props.getProperty(PROP_ESCALATE_ON_ERROR))) {
            b.dontEscalateOnError();
        }
        if ("true".equals(props.getProperty(PROP_USE_SHUTDOWN_HOOK))) {
            b.useShutdownHook();
        } else {
            b.dontUseShutdownHook();
        }

        String rotateAfter = props.getProperty(PROP_LOG_ROTATION_MAX_SIZE_MB);
        long rotationMaxSize = -1;
        if (rotateAfter != null) {
            try {
                rotationMaxSize = Long.parseLong(rotateAfter);
            } catch (NumberFormatException nfe) {
                LoggingLogging.log("Log rotation max size not parseable: "
                        + rotateAfter + " for " + PROP_LOG_ROTATION_MAX_SIZE_MB, nfe);
            }
        }

        if (props.containsKey(PROP_ASYNC_LOGGING_THREAD_PRIORITY)) {
            String priorityString = props.getProperty(PROP_ASYNC_LOGGING_THREAD_PRIORITY);
            int priority;
            switch (priorityString.toLowerCase().replace('_', '-')) {
                case "max-priority":
                case "max":
                case "maximum":
                    priority = Thread.MAX_PRIORITY;
                    break;
                case "norm-priority":
                case "norm":
                case "normal":
                    priority = Thread.NORM_PRIORITY;
                    break;
                case "min":
                case "min-priority":
                case "minimum":
                    priority = Thread.MIN_PRIORITY;
                    break;
                default:
                    try {
                    priority = Integer.parseInt(priorityString);
                } catch (NumberFormatException e) {
                    LoggingLogging.log(true, "Thread priority is not a number or constant: '" + priorityString + "'");
                    priority = Thread.NORM_PRIORITY;
                }
            }
            b.asyncLoggingThreadPriority(priority);
        }
        String defaultPolicy = props.getProperty(PROP_DEFAULT_CONFIG_POLICY);
        if (defaultPolicy != null) {
            defaultPolicy = defaultPolicy.toLowerCase().replace('_', '-');
            switch (defaultPolicy) {
                case PROP_VALUE_DONT_SET_AS_DEFAULT_CONFIG:
                    b.nonDefault();
                    break;
                case PROP_VALUE_USE_AS_DEFAULT_CONFIG_IF_UNSET:
                    b.becomeGlobalConfigIfGlobalConfigIsUnset();
                    break;
                case PROP_VALUE_TAKE_OVER_AS_DEFAULT_CONFIG:
                    b.takeOverGlobalLoggingConfig();
                    break;
                default:
                    LoggingLogging.log(true, "Unknown " + PROP_DEFAULT_CONFIG_POLICY
                            + " '" + defaultPolicy + "' - using default of "
                            + b.defaultConfigReplacementPolicy);
            }
        }
        String jsonPolicy = props.getProperty(PROP_JSON_SERIALIZATION_POLICY);
        if (jsonPolicy != null) {
            jsonPolicy = jsonPolicy.toLowerCase().replace('_', '-');
            switch (jsonPolicy) {
                case PROP_VALUE_JSON_SERIALIZATION_POLICY_ADAPTIVE:
                    b.useJacksonWhenNeededForJSON();
                    break;
                case PROP_VALUE_JSON_SERIALIZATION_POLICY_ALWAYS_JACKSON:
                    b.useJacksonExclusivelyForJSON();
                    break;
                case PROP_VALUE_JSON_SERIALIZATION_POLICY_NEVER_JACKSON:
                    b.neverUseJacksonForJSON();
                    break;
                default:
                    LoggingLogging.log(true, "Unknown " + PROP_JSON_SERIALIZATION_POLICY
                            + " '" + jsonPolicy + "'.  Using default of "
                            + b.jsonSerializationPolicy + ".");
            }
        }
        String routed = props.getProperty(PROP_ROUTED_LOGGERS);
        if (routed != null) {
            Set<CharSequence> names = Strings.splitUniqueNoEmpty(',', routed);
            for (CharSequence seq : names) {
                String prop = PROP_ROUTE_PREFIX + seq;
                String filename = props.getProperty(prop);
                if (filename != null) {
                    b.routeLogsTo(Paths.get(filename), seq.toString());
                }
                String levelProp = PROP_ROUTE_LEVEL_PREFIX + seq;
                String level = props.getProperty(levelProp);
                if (level != null) {
                    LogLevel logLevel = LogLevel.valueOf(level);
                    switch (logLevel.intValue()) {
                        case FATAL_LEVEL:
                            b.setMinimumLogLevelToFatalFor(seq.toString());
                            break;
                        case ERROR_LEVEL:
                            b.setMinimumLogLevelToErrorFor(seq.toString());
                            break;
                        case WARN_LEVEL:
                            b.setMinimumLogLevelToWarnFor(seq.toString());
                            break;
                        case INFO_LEVEL:
                            b.setMinimumLogLevelToInfoFor(seq.toString());
                            break;
                        case DEBUG_LEVEL:
                            b.setMinimumLogLevelToDebugFor(seq.toString());
                            break;
                        case TRACE_LEVEL:
                            b.setMinimumLogLevelToTraceFor(seq.toString());
                            break;
                        default:
                            throw new ConfigurationError(logLevel.toString());
                    }
                }
            }
        }
        return b.build();
    }

    static final class SequenceDecorator implements LoggableConsumer<Log> {

        @JsonIgnore
        private final AtomicLong seq;
        @JsonIgnore
        private final String key;

        SequenceDecorator() {
            this(0L, "seq");
        }

        SequenceDecorator(long start, String key) {
            this.seq = new AtomicLong(start);
            this.key = notNull("key", key);
        }

        @JsonProperty("current")
        public long currentValue() {
            return seq.get();
        }

        @JsonProperty("name")
        public String name() {
            return key;
        }

        @Override
        public void accept(Log t) {
            t.add(key, seq.getAndIncrement());
        }

        @Override
        public String toString() {
            return "sequence-numbers(" + key + " currently " + seq.get() + ")";
        }
    }

    enum DefaultLoggingConfigHandling {
        NON_DEFAULT,
        SET_IF_UNSET,
        TAKE_OVER;

        @Override
        public String toString() {
            return name().toLowerCase().replace('_', '-');
        }

        boolean isSetIt() {
            switch (this) {
                case NON_DEFAULT:
                    return false;
                case SET_IF_UNSET:
                case TAKE_OVER:
                    return true;
                default:
                    throw new AssertionError(this);
            }
        }

        boolean isForce() {
            return this == TAKE_OVER;
        }
    }

    enum JsonSerializationPolicy {
        ADAPTIVE,
        ALWAYS_JACKSON,
        NEVER_JACKSON;

        @Override
        public String toString() {
            return name().toLowerCase().replace('_', '-');
        }
    }

    public static final class Builder {

        private Consumer<ObjectMapper> mapperConsumer = LoggableConsumer.noop();
        private final DefaultLogLevel dll = new DefaultLogLevel();
        private final Map<String, LogLevel> minLogLevelForLogger = CollectionUtils.supplierMap(dll);
        private final Map<String, Path> logFileForLogger = new HashMap<>();
        private final Map<String, LogSink> logSinkForLogName = new HashMap<>();
        private Path logPathForSevere = null;
        private LogSink logSinkForSevere = null;
        private Path defaultLogFile;
        private Boolean console;
        private boolean async = false;
        private LogSink defaultLogSink;
        private ThrowingRunnable onShutdown;
        private Consumer<Log> decorator;
        private boolean recordCaller;
        private int asyncThreads = 1;
        private int asyncThreadPriority = Thread.NORM_PRIORITY - 1;
        private final LoggingConfigSupplier configSupplier = new LoggingConfigSupplier();
        private DefaultLoggingConfigHandling defaultConfigReplacementPolicy = DefaultLoggingConfigHandling.SET_IF_UNSET;
        private ObjectMapper mapper;
        private JsonSerializationPolicy jsonSerializationPolicy = JsonSerializationPolicy.ADAPTIVE;
        private String hostname;
        private boolean escalateOnError = true;
        private boolean useShutdownHook = true;
        private long rotateFilesAboveMb = -1;

        public Builder fileRotationThresholdMegabytes(long val) {
            rotateFilesAboveMb = val;
            return this;
        }

        public Builder dontUseShutdownHook() {
            useShutdownHook = false;
            return this;
        }

        public Builder useShutdownHook() {
            useShutdownHook = true;
            return this;
        }

        public Builder dontEscalateOnError() {
            escalateOnError = false;
            return this;
        }

        public Builder escalateOnError() {
            escalateOnError = true;
            return this;
        }

        public Builder hostNameForLogRecords(String hostname) {
            this.hostname = notNull("hostname", hostname);
            return this;
        }

        public Builder useJacksonExclusivelyForJSON() {
            jsonSerializationPolicy = JsonSerializationPolicy.ALWAYS_JACKSON;
            return this;
        }

        public Builder useJacksonWhenNeededForJSON() {
            jsonSerializationPolicy = ADAPTIVE;
            return this;
        }

        public Builder neverUseJacksonForJSON() {
            jsonSerializationPolicy = NEVER_JACKSON;
            return this;
        }

        public Builder withObjectMapper(ObjectMapper mapper) {
            this.mapper = notNull("mapper", mapper);
            return this;
        }

        public Builder asyncLoggingThreadPriority(int value) {
            asyncThreadPriority = value;
            return this;
        }

        private boolean existsOrCanCreate(Path dirPath, Path target) {
            if (dirPath == null) {
                throw new ConfigurationError("Cannot create parent directories of " + target);
            }
            if (Files.exists(dirPath)) {
                if (Files.isDirectory(dirPath)) {
                    if (!Files.isWritable(dirPath)) {
                        throw new ConfigurationError("Will not be able "
                                + "to create subdirectories in " + dirPath
                                + " in order to create log file " + target
                        );
                    }
                    return true;
                } else {
                    throw new ConfigurationError("Parent " + dirPath + " of " + target
                            + " is not a directory");
                }
            } else {
                return existsOrCanCreate(dirPath.getParent(), target);
            }
        }

        private void checkUsable(Path path) {
            if (Files.exists(path)) {
                if (!Files.isWritable(path)) {
                    throw new ConfigurationError("No write privileges on log file " + path);
                }
            } else {
                if (!existsOrCanCreate(path.getParent(), path)) {
                    throw new ConfigurationError("Will not be able to use log file " + path);
                }
            }
        }

        public Builder nonDefault() {
            defaultConfigReplacementPolicy = NON_DEFAULT;
            return this;
        }

        public Builder becomeGlobalConfigIfGlobalConfigIsUnset() {
            defaultConfigReplacementPolicy = SET_IF_UNSET;
            return this;
        }

        public Builder takeOverGlobalLoggingConfig() {
            defaultConfigReplacementPolicy = TAKE_OVER;
            return this;
        }

        public Builder asyncLoggingThreads(int threads) {
            this.asyncThreads = greaterThanZero("threads", threads);
            return this;
        }

        public Builder recordCaller() {
            recordCaller = true;
            return this;
        }

        public Builder logErrorAndFatalTo(Path path) {
            checkUsable(notNull("path", path));
            if (logPathForSevere != null && !notNull("path", path).equals(logPathForSevere)) {
                throw new IllegalStateException("Already have a path for severe "
                        + "errors: " + logPathForSevere + " - cannot set it to " + path);
            }
            logPathForSevere = path;
            return this;
        }

        public Builder logErrorAndFatalTo(LogSink sink) {
            if (logSinkForSevere != null) {
                logSinkForSevere = logSinkForSevere.and(notNull("sink", sink));
            } else {
                logSinkForSevere = notNull("sink", sink);
            }
            return this;
        }

        public Builder withSequenceNumbers() {
            return withLogDecorator(new SequenceDecorator());
        }

        public Builder withSequenceNumbers(String key) {
            return withSequenceNumbers(notNull("key", key), 0L);
        }

        public Builder withSequenceNumbers(long start) {
            return withSequenceNumbers("seq", start);
        }

        public Builder withSequenceNumbers(String key, long start) {
            return withLogDecorator(new SequenceDecorator(start, notNull("key", key)));
        }

        public Builder routeLogsTo(LogSink to, String... names) {
            notNull("to", to);
            for (String name : names) {
                logSinkForLogName.put(name, to);
            }
            return this;
        }

        public Builder withLogDecorator(Consumer<Log> decorator) {
            notNull("decorator", decorator);
            if (this.decorator == null) {
                this.decorator = decorator;
            } else {
                this.decorator = this.decorator.andThen(decorator);
            }
            return this;
        }

        public LoggingConfig build() {
            if (configSupplier.config != null) {
                throw new IllegalStateException("build() called twice");
            }
            onShutdown = ThrowingRunnable.oneShot(true);
            ObjectMapper mapperLocal = this.mapper == null ? new ObjectMapper() : this.mapper;
            mapperConsumer.accept(mapperLocal);
            BiPredicate<String, LogLevel> specificConfig = new LevelConfig(minLogLevelForLogger, dll.minimum);
            LogSink fallback = defaultLogSink;
            long rotateFilesAboveBytes = max(0L, rotateFilesAboveMb * 1024 * 1024);
            if (defaultLogFile != null) {
                LogSink files;
                if (rotateFilesAboveBytes <= 0) {
                    FileLogSink fs = new FileLogSink(defaultLogFile, configSupplier);
                    onShutdown.andAlways(fs);
                    files = fs;
                } else {
                    FileRotationLogSink fr = new FileRotationLogSink(rotateFilesAboveBytes, defaultLogFile, configSupplier);
                    files = fr;
                    onShutdown.andAlways(fr);
                }
                fallback = fallback == null ? files
                        : fallback.and(files);
                if (console != null && console) {
                    fallback = fallback.and(new ConsoleLogSink(configSupplier));
                }
            } else {
                fallback = defaultLogSink == null ? new ConsoleLogSink(configSupplier)
                        : defaultLogSink.and(new ConsoleLogSink(configSupplier));
            }
            LogRecordRouter sp = new LogRecordRouter(
                    minLogLevelForLogger, logFileForLogger, fallback, async, onShutdown, logSinkForLogName,
                    logPathForSevere, logSinkForSevere, configSupplier, rotateFilesAboveBytes);

            LoggingConfig result = new LoggingConfig(mapperLocal, dll.minimum,
                    specificConfig, sp, onShutdown, decorator, recordCaller, asyncThreads,
                    defaultConfigReplacementPolicy, asyncThreadPriority,
                    jsonSerializationPolicy, hostname, escalateOnError, useShutdownHook);
            configSupplier.config = result;
            return result;
        }

        static final class LevelConfig implements BiPredicate<String, LogLevel> {

            @JsonProperty("minLevels")
            private final Map<String, LogLevel> minLogLevelForLogger;
            @JsonProperty("default")
            private final int defaultMinLevel;

            public LevelConfig(Map<String, LogLevel> minLogLevelForLogger, int defaultMinLevel) {
                this.minLogLevelForLogger = minLogLevelForLogger;
                this.defaultMinLevel = defaultMinLevel;
            }

            public String toString() {
                return CollectionUtils.invert(minLogLevelForLogger).toString();
            }

            @Override
            public boolean test(String t, LogLevel u) {
                LogLevel lev = minLogLevelForLogger.get(t);
                int val = lev != null ? lev.intValue() : defaultMinLevel;
                return val >= u.intValue();
            }
        }

        public Builder logTo(LogSink sink) {
            if (defaultLogSink == null) {
                defaultLogSink = sink;
            } else {
                defaultLogSink = defaultLogSink.and(sink);
            }
            return this;
        }

        public Builder logToConsole() {
            console = true;
            return this;
        }

        public Builder logToFile(Path file) {
            defaultLogFile = file;
            return this;
        }

        public Builder asyncLogging() {
            async = true;
            return this;
        }

        public Builder routeLogsTo(Path file, String... logs) {
            checkUsable(file);
            for (String log : logs) {
                logFileForLogger.put(log, file);
            }
            return this;
        }

        public Builder configureJsonMappingWith(Consumer<ObjectMapper> mapperConsumer) {
            this.mapperConsumer = this.mapperConsumer.andThen(mapperConsumer);
            return this;
        }

        public Builder setMinimumLogLevelToError() {
            dll.setMinimum(LogLevel.ERROR.intValue());
            return this;
        }

        public Builder setMinimumLogLevelToInfo() {
            dll.setMinimum(LogLevel.INFO.intValue());
            return this;
        }

        public Builder setMinimumLogLevelToTrace() {
            dll.setMinimum(LogLevel.TRACE.intValue());
            return this;
        }

        public Builder setMinimumLogLevelToDebug() {
            dll.setMinimum(LogLevel.DEBUG.intValue());
            return this;
        }

        public Builder setMinimumLogLevelToFatal() {
            dll.setMinimum(LogLevel.FATAL.intValue());
            return this;
        }

        public Builder setMinimumLogLevelToWarn() {
            dll.setMinimum(LogLevel.WARN.intValue());
            return this;
        }

        private Builder setLevelForSpecific(LogLevel level, String... logs) {
            for (String log : logs) {
                minLogLevelForLogger.put(log, level);
            }
            return this;
        }

        public Builder setMinimumLogLevelToFatalFor(String... logs) {
            return setLevelForSpecific(LogLevel.FATAL, logs);
        }

        public Builder setMinimumLogLevelToErrorFor(String... logs) {
            return setLevelForSpecific(LogLevel.ERROR, logs);
        }

        public Builder setMinimumLogLevelToWarnFor(String... logs) {
            return setLevelForSpecific(LogLevel.INFO, logs);
        }

        public Builder setMinimumLogLevelToInfoFor(String... logs) {
            return setLevelForSpecific(LogLevel.INFO, logs);
        }

        public Builder setMinimumLogLevelToDebugFor(String... logs) {
            return setLevelForSpecific(LogLevel.DEBUG, logs);
        }

        public Builder setMinimumLogLevelToTraceFor(String... logs) {
            return setLevelForSpecific(LogLevel.TRACE, logs);
        }
    }

    static final class LoggingConfigSupplier implements Supplier<LoggingConfig> {

        LoggingConfig config;

        @Override
        public LoggingConfig get() {
            if (config == null) {
                // Race condition when a new logging config registered
                // by guice is taking over for no config, and closes
                // a bunch of temporary configs
                LoggingConfig to = TAKING_OVER.get();
                if (to != null) {
                    return to;
                }
                throw new IllegalStateException("Called before LoggingConfig initialized");
            }
            return config;
        }
    }

    static final class DefaultLogLevel implements Supplier<LogLevel> {

        int minimum = LogLevel.INFO.intValue();

        DefaultLogLevel setMinimum(int minimum) {
            this.minimum = minimum;
            return this;
        }

        @Override
        @JsonValue
        public LogLevel get() {
            return LogLevel.valueOf(minimum);
        }
    }
}
