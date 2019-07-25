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
package com.mastfrog.bunyan.v2.mongodb.sink;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.bunyan.java.v2.JSONContext;
import com.mastfrog.bunyan.java.v2.LogSink;
import static com.mastfrog.giulius.bunyan.java.v2.LoggingModule.GUICE_BINDING_DEFAULT_SINK;
import com.mastfrog.giulius.mongodb.async.GiuliusMongoAsyncModule;
import com.mastfrog.giulius.mongodb.async.MongoFutureCollection;
import com.mastfrog.mongodb.init.MongoInitModule;
import com.mastfrog.settings.Settings;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mastfrog.util.time.TimeUtil;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
public class BunyanMongodbModule extends AbstractModule {

    public static final String SETTINGS_KEY_LOG_COLLECTION = "log-collection";
    private String logCollection = "logs";
    private volatile boolean initialized;
    public static final String GUICE_BINDING_LOG_COLLECTION = "_logs";

    public BunyanMongodbModule() {

    }

    public BunyanMongodbModule(Settings settings) {
        logCollection = settings.getString(SETTINGS_KEY_LOG_COLLECTION, logCollection);
    }

    public BunyanMongodbModule setLogCollection(String name) {
        notNull("name", name);
        if (!"logs".equals(logCollection) && !logCollection.equals(name)) {
            throw new ConfigurationError("Log collection already bound to '" + logCollection + "'"
                    + " (perhaps from Settings?)");
        }
        if (initialized) {
            throw new IllegalStateException("Cannot set the log collection "
                    + "when running.");
        }
        this.logCollection = name;
        return this;
    }

    @Override
    protected void configure() {
        bind(LogSink.class).annotatedWith(Names.named(GUICE_BINDING_DEFAULT_SINK))
                .to(MongoLogSink.class).asEagerSingleton();
        install(new GiuliusMongoAsyncModule().bindCollection(
                GUICE_BINDING_LOG_COLLECTION, logCollection, LogRecord.class));
        install(new MongoInitModule().withCollections()
                .add(logCollection)
                //                .capped(true)
                //                .sizeInBytes(1024 * 1024 * 1024 * 16)
                //                .maxDocuments(4096)
                .ensureIndex("level_index").indexDescending("level").buildIndex()
                .ensureIndex("time_index").indexAscending("time").buildIndex()
                .ensureIndex("hostname").indexHashed("hostname").buildIndex()
                .ensureIndex("msg_index").indexHashed("msg").buildIndex()
                .buildCollection()
                .build());
        initialized = true;
    }

    public static final class LogRecord extends LinkedHashMap<String, Object> {

        private static final Date EPOCH = new Date(0);

        public LogRecord(Map<String, Object> map) {
            Object time = map.get("time");
            putAll(map);
            if (time instanceof String) {
                map.remove("time");
                ZonedDateTime t = TimeUtil.fromIsoFormat((String) time);
                Date date = new Date(TimeUtil.toUnixTimestamp(t));
                put("time", date);
            } else {
                put("time", EPOCH);
            }
        }
    }

    static final class MongoLogSink implements LogSink {

        private final Provider<MongoFutureCollection<LogRecord>> collection;
        private final AtomicLong succesfullyWritten = new AtomicLong();

        @Inject
        public MongoLogSink(
                @Named(GUICE_BINDING_LOG_COLLECTION) Provider<MongoFutureCollection<LogRecord>> collection) {
            this.collection = collection;
        }

        public String toString() {
            return "MongoLogSink(" + collection.get() + ")";
        }

        public long written() {
            return succesfullyWritten.get();
        }

        @Override
        public void push(JSONContext ctx, Map<String, Object> logrecord) {
            collection.get().insertOne(new LogRecord(logrecord))
                    .handleAsync((ignored, thrown) -> {
                        if (thrown != null) {
                            thrown.printStackTrace(System.err);
                        } else {
                            succesfullyWritten.incrementAndGet();
                        }
                        return null;
                    });
        }
    }
}
