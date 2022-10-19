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
package com.mastfrog.giulius.bunyan.java.v2;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.bunyan.java.v2.LogSink;
import com.mastfrog.bunyan.java.v2.LoggingConfig;
import com.mastfrog.bunyan.java.v2.Logs;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.jackson.configuration.DurationSerializationMode;
import com.mastfrog.jackson.configuration.JacksonConfigurer;
import com.mastfrog.jackson.configuration.TimeSerializationMode;
import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
public final class LoggingModule extends AbstractModule {

    private final List<String> loggers = new LinkedList<>();
    /**
     * Settings key for the file to write to, if any.
     */
    public static final String SETTINGS_KEY_LOG_FILE = "log.file";
    /**
     * Settings key for the minimum log level.
     */
    public static final String SETTINGS_KEY_LOG_LEVEL = "log.level";
    /**
     * Settings key for the host name used in log records (if not set, will be
     * gotten from the system).
     */
    public static final String SETTINGS_KEY_LOG_HOSTNAME = "log.hostname";
    /**
     * Settings key - if true, log records are buffered and written by a
     * background thread; more performant but may result in data loss on crash.
     */
    public static final String SETTINGS_KEY_ASYNC_LOGGING = "log.async";
    /**
     * Settings key for whether logging should also be written to the system out
     * (this is the default unless a file is set).
     */
    public static final String SETTINGS_KEY_LOG_TO_CONSOLE = "log.console";

    public static final String SETTINGS_KEY_LOG_SEVERE_TO_FILE = "log.severe.file";

    public static final String SETTINGS_KEY_LOG_SEQUENCE_NUMBERS = "log.seq";
    public static final String SETTINGS_KEY_LOG_CALLER = "log.caller";
    public static final String SETTINGS_KEY_ROUTED_LOGS = "log.route";
    public static final String SETTINGS_KEY_ROUTED_LOG_PREFIX = "log.route.";
    public static final String SETTINGS_KEY_ROUTED_LOG_LEVEL_PREFIX = "log.level.";

    public static final String SETTINGS_KEY_ASYNC_THREADS_PRIORITY = "log.async.thread.priority";

    public static final String SETTINGS_KEY_JSON_SERIALIZATION_POLICY = "log.json.policy";
    public static final String SETTINGS_VALUE_JSON_POLICY_ADAPTIVE = LoggingConfig.PROP_VALUE_JSON_SERIALIZATION_POLICY_ADAPTIVE;
    public static final String SETTINGS_VALUE_JSON_POLICY_ALWAYS_JACKSON = LoggingConfig.PROP_VALUE_JSON_SERIALIZATION_POLICY_ALWAYS_JACKSON;
    public static final String SETTINGS_VALUE_JSON_POLICY_NEVER_JACKSON = LoggingConfig.PROP_VALUE_JSON_SERIALIZATION_POLICY_NEVER_JACKSON;

    public static final String SETTINGS_KEY_USE_AS_DEFAULT_CONFIG = "log.config.policy";
    public static final String SETTINGS_VALUE_DONT_USE_AS_DEFAULT_CONFIG = LoggingConfig.PROP_VALUE_DONT_SET_AS_DEFAULT_CONFIG;
    public static final String SETTINGS_VALUE_USE_AS_DEFAULT_CONFIG_IF_UNSET = LoggingConfig.PROP_VALUE_USE_AS_DEFAULT_CONFIG_IF_UNSET;
    public static final String SETTINGS_VALUE_TAKE_OVER_DEFAULT_CONFIG = LoggingConfig.PROP_VALUE_TAKE_OVER_AS_DEFAULT_CONFIG;

    public static final String SETTINGS_KEY_ESCALATE_ON_ERRORS = "log.escalate.errors";
    /**
     * Name used by the Named annotation to identify the ObjectMapper that will
     * be injected into loggers. If unusual objects are to be serialized into
     * log records, it will need to be configured to handle them.
     */
    public static final String GUICE_BINDING_OBJECT_MAPPER = "bunyan-java";

    /**
     * If a AbstractLogSink is bound with this name, it will be used as the
     * default log sink.
     */
    public static final String GUICE_BINDING_DEFAULT_SINK = "defaultSink";
    private final JacksonModule jacksonModule;
    private boolean dontBindLoggingConfig = false;
    private boolean dontConfigurePathSerialization;

    public LoggingModule() {
        this(true);
    }

    public LoggingModule dontBindLoggingConfig() {
        dontBindLoggingConfig = true;
        return this;
    }

    /**
     * Create a new logging modules.
     *
     * @param useMetaInfServicesJacksonConfigurers If true, use the Java
     * Extension Mechanism to look up JacksonConfigurers on the classpath, which
     * will be used to configure the ObjectMapper used to render log records as
     * JSON.
     */
    public LoggingModule(boolean useMetaInfServicesJacksonConfigurers) {
        jacksonModule = new JacksonModule(GUICE_BINDING_OBJECT_MAPPER, useMetaInfServicesJacksonConfigurers)
                .withConfigurer(new JacksonConfig())
                .withJavaTimeSerializationMode(TimeSerializationMode.TIME_AS_ISO_STRING,
                        DurationSerializationMode.DURATION_AS_STRING);
    }

    /**
     * Add the name of a logger that should be bound, which can be injected
     * using the Named annotation.
     *
     * @param name The name of the logger
     * @return This
     */
    public LoggingModule bindLogger(String name) {
        if (!loggers.contains(name)) {
            loggers.add(name);
        }
        return this;
    }

    /**
     * Include a particular JacksonConfigurer to configure the ObjectMapper used
     * to serialize log lines. Note that you will write broken logs if you use
     * any pretty-printing options that split lines here - this is why we have a
     * separate Guice binding for this mapper.
     *
     * @param configurer The configurer.
     * @return This
     */
    public LoggingModule withConfigurer(JacksonConfigurer configurer) {
        jacksonModule.withConfigurer(configurer);
        return this;
    }

    /**
     * By default, we override Jackson's serialization of java.nio.Path, which
     * uses URIs, to simply return toString(), for simplicity in logs, where
     * presumably you know if what you're looking it is or is not supposed to be
     * a file. If you do not want that, or are doing your own other
     * serialization of paths, call this method.
     *
     * @return this
     */
    public LoggingModule dontConfigurePathSerialization() {
        this.dontConfigurePathSerialization = true;
        return this;
    }

    static class JC extends JsonSerializer<Path> implements JacksonConfigurer {

        @Override
        public ObjectMapper configure(ObjectMapper m) {
            SimpleModule sm = new SimpleModule("LoggingModule.JC");
            sm.addSerializer(this);
            m.registerModule(sm);
            return m;
        }

        @Override
        public Class<Path> handledType() {
            return Path.class;
        }

        @Override
        public void serialize(Path t, JsonGenerator jg, SerializerProvider sp) throws IOException {
            jg.writeString(t.toString());
        }
    }

    @Override
    protected void configure() {
        if (!dontConfigurePathSerialization) {
            jacksonModule.withConfigurer(new JC());
        }
        loggers.forEach((s) -> {
            bind(Logs.class).annotatedWith(Names.named(s))
                    .toProvider(new LogsProvider(s, binder().getProvider(LoggingConfig.class)));
        });
        install(jacksonModule);
        if (!dontBindLoggingConfig) {
            bind(LoggingConfig.class).toProvider(LoggingConfigProvider.class).asEagerSingleton();
        }
//        bind(AbstractLogSink.class).annotatedWith(Names.named("_defaultLogSinkProxy"))
//                .toProvider(DefaultLogSinkProvider.class).asEagerSingleton();

    }

    private static class LogsProvider implements com.google.inject.Provider<Logs> {

        private final String name;
        private final com.google.inject.Provider<LoggingConfig> loggers;

        public LogsProvider(String name, com.google.inject.Provider<LoggingConfig> loggers) {
            this.name = name;
            this.loggers = loggers;
        }

        @Override
        public Logs get() {
            return loggers.get().logs(name);
        }
    }

//    static final class DefaultLogSinkProvider implements Provider<AbstractLogSink> {
//
//        private @Inject(optional = true)
//        @Named(GUICE_BINDING_DEFAULT_SINK)
//        AbstractLogSink defaultSink;
//        private final LogSink lazyDelegator;
//
//        @Inject
//        DefaultLogSinkProvider(Provider<LoggingConfig> config) {
//            lazyDelegator = AbstractLogSink.lazy(config::get, this::defaultSink);
//        }
//
//        AbstractLogSink defaultSink() {
//            return defaultSink;
//        }
//
//        @Override
//        public AbstractLogSink get() {
//            return lazyDelegator;
//        }
//    }
    static final class LoggingConfigProvider implements Provider<LoggingConfig> {

        private final Settings settings;
        private LoggingConfig config;
        private final ShutdownHookRegistry reg;
        private final ObjectMapper mapper;
        @Inject(optional = true)
        private @Named(GUICE_BINDING_DEFAULT_SINK)
        Provider<LogSink> delegatingDefaultSink;

        @Inject
        public LoggingConfigProvider(Settings settings, ShutdownHookRegistry reg,
                @Named(GUICE_BINDING_OBJECT_MAPPER) ObjectMapper mapper) {
            this.settings = settings;
            this.reg = reg;
            this.mapper = mapper;
        }

        @Override
        public synchronized LoggingConfig get() {
            if (config != null) {
                return config;
            }
            LoggingConfig.Builder b = LoggingConfig
                    .builder().withObjectMapper(mapper);
            if (delegatingDefaultSink != null) {
                LogSink defaultSink = delegatingDefaultSink.get();
                if (delegatingDefaultSink != null) {
                    b.logTo(defaultSink);
                }
            }
            String defaultLogFile = settings.getString(SETTINGS_KEY_LOG_FILE);
            if (defaultLogFile != null) {
                b.logToFile(Paths.get(defaultLogFile));
                if (settings.getBoolean(SETTINGS_KEY_LOG_TO_CONSOLE, false)) {
                    b.logToConsole();
                }
            }
            if (settings.getBoolean(SETTINGS_KEY_ASYNC_LOGGING, false)) {
                b.asyncLogging();
            }
            if (settings.getString(SETTINGS_KEY_LOG_HOSTNAME) != null) {
                b.hostNameForLogRecords(settings.getString(SETTINGS_KEY_LOG_HOSTNAME));
            }
            if (settings.getBoolean(SETTINGS_KEY_LOG_SEQUENCE_NUMBERS, false)) {
                b.withSequenceNumbers();
            }
            if (settings.getBoolean(SETTINGS_KEY_LOG_CALLER, false)) {
                b.recordCaller();
            }
            if (settings.getString(SETTINGS_KEY_ASYNC_THREADS_PRIORITY) != null) {
                b.asyncLoggingThreadPriority(settings.getInt(SETTINGS_KEY_ASYNC_THREADS_PRIORITY));
            }
            if (settings.getString(SETTINGS_KEY_LOG_SEVERE_TO_FILE) != null) {
                b.logErrorAndFatalTo(Paths.get(settings.getString(SETTINGS_KEY_LOG_SEVERE_TO_FILE)));
            }
            if (settings.getString(SETTINGS_KEY_ESCALATE_ON_ERRORS) != null) {
                if (settings.getBoolean(SETTINGS_KEY_ESCALATE_ON_ERRORS, true)) {
                    b.escalateOnError();
                } else {
                    b.dontEscalateOnError();
                }
            }
            String routedLogs = settings.getString(SETTINGS_KEY_ROUTED_LOGS);
            if (routedLogs != null) {
                Set<CharSequence> all = Strings.splitUniqueNoEmpty(',', routedLogs);
                for (CharSequence logName : all) {
                    // This makes me want to put LogLevel in the API...almost, but
                    // it's still cleaner without it.
                    String file = settings.getString(SETTINGS_KEY_ROUTED_LOG_PREFIX + logName);
                    if (file != null) {
                        b.routeLogsTo(Paths.get(file), logName.toString());
                    }
                    String levelProp = LoggingModule.SETTINGS_KEY_ROUTED_LOG_LEVEL_PREFIX + logName;
                    String level = settings.getString(levelProp);
                    if (level != null) {
                        switch (level.toLowerCase()) {
                            case "fatal":
                            case "severe":
                            case "60":
                                b.setMinimumLogLevelToFatalFor(logName.toString());
                                break;
                            case "error":
                            case "50":
                                b.setMinimumLogLevelToErrorFor(logName.toString());
                                break;
                            case "warn":
                            case "warning":
                            case "40":
                                b.setMinimumLogLevelToWarnFor(logName.toString());
                                break;
                            case "info":
                            case "30":
                                b.setMinimumLogLevelToInfoFor(logName.toString());
                                break;
                            case "debug":
                            case "20":
                                b.setMinimumLogLevelToDebugFor(logName.toString());
                                break;
                            case "trace":
                            case "10":
                                b.setMinimumLogLevelToTraceFor(logName.toString());
                                break;
                            default:
                                throw new ConfigurationError(level);
                        }
                    }
                }
            }

            String level = settings.getString(SETTINGS_KEY_LOG_LEVEL);
            if (level != null) {
                switch (level.toLowerCase()) {
                    case "fatal":
                    case "60":
                        b.setMinimumLogLevelToFatal();
                        break;
                    case "error":
                    case "50":
                        b.setMinimumLogLevelToError();
                        break;
                    case "warn":
                    case "warning":
                    case "40":
                        b.setMinimumLogLevelToWarn();
                        break;
                    case "info":
                    case "30":
                        b.setMinimumLogLevelToInfo();
                        break;
                    case "debug":
                    case "20":
                        b.setMinimumLogLevelToDebug();
                        break;
                    case "trace":
                    case "10":
                        b.setMinimumLogLevelToTrace();
                        break;
                    default:
                        throw new ConfigurationError("Unknown log level '" + level + "'");
                }
            }
            String jsonPolicy = settings.getString(SETTINGS_KEY_JSON_SERIALIZATION_POLICY);
            if (jsonPolicy != null) {
                switch (jsonPolicy.toLowerCase().replace('_', '-')) {
                    case SETTINGS_VALUE_JSON_POLICY_ADAPTIVE:
                        b.useJacksonWhenNeededForJSON();
                        break;
                    case SETTINGS_VALUE_JSON_POLICY_ALWAYS_JACKSON:
                        b.useJacksonExclusivelyForJSON();
                        break;
                    case SETTINGS_VALUE_JSON_POLICY_NEVER_JACKSON:
                        b.neverUseJacksonForJSON();
                        break;
                    default:
                        throw new ConfigurationError("Erroneous value for "
                                + SETTINGS_KEY_JSON_SERIALIZATION_POLICY + ": '"
                                + jsonPolicy + "'");
                }
            }
            String configPolicy = settings.getString(SETTINGS_KEY_USE_AS_DEFAULT_CONFIG);
            if (configPolicy != null) {
                switch (configPolicy.toLowerCase().replace('_', '-')) {
                    case SETTINGS_VALUE_DONT_USE_AS_DEFAULT_CONFIG:
                        b.nonDefault();
                        break;
                    case SETTINGS_VALUE_USE_AS_DEFAULT_CONFIG_IF_UNSET:
                        b.becomeGlobalConfigIfGlobalConfigIsUnset();
                        break;
                    case SETTINGS_VALUE_TAKE_OVER_DEFAULT_CONFIG:
                        b.takeOverGlobalLoggingConfig();
                        break;
                    default:
                        throw new ConfigurationError("Erroneous value for "
                                + SETTINGS_KEY_JSON_SERIALIZATION_POLICY + ": '"
                                + configPolicy + "'");
                }
            }
            LoggingConfig result = b.build();
            reg.addResource(result);
            return config = result;
        }
    }
}
