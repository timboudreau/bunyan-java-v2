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

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.mastfrog.bunyan.java.v2.LogSink;
import com.mastfrog.bunyan.java.v2.Logs;
import static com.mastfrog.bunyan.v2.mongodb.sink.BunyanMongodbModule.GUICE_BINDING_LOG_COLLECTION;
import com.mastfrog.bunyan.v2.mongodb.sink.BunyanMongodbModule.MongoLogSink;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.bunyan.java.v2.LoggingModule;
import static com.mastfrog.giulius.bunyan.java.v2.LoggingModule.GUICE_BINDING_DEFAULT_SINK;
import com.mastfrog.giulius.mongodb.async.MongoFutureCollection;
import com.mastfrog.giulius.mongodb.async.MongoHarness;
import com.mastfrog.giulius.mongodb.async.TestSupport;
import com.mastfrog.settings.Settings;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class BunyanMongodbModuleTest {

    Dependencies deps;
    Logs foo;
    Logs bar;
    MongoFutureCollection<Document> records;
    private LogSink dlsink;

    @Test
    public void testRecordsAreStored() throws Exception {
        assertNotNull(dlsink);
        foo.error("launched").add("foo", "bar").close();
        bar.error("stuff").add("moo", "cheese").close();
        for (int i = 0; i < 20; i++) {
            int ix = i;
            foo.warn("whoopee", lg -> {
                lg.add("hey", ix).add("Moofy", "Poodle")
                        .close();
            });
        }
        while (((MongoLogSink) dlsink).written() < 22) {
            Thread.sleep(20);
        }
        Thread.sleep(1000);
        TestSupport.await(30, TimeUnit.SECONDS, ts -> {
            records.findOne(new Document("name", "foo").append("msg", "launched"))
                    .handle((Document data, Throwable thrown) -> {
                        ts.callback((Document found) -> {
                            assertNotNull(found);
                            assertEquals("foo", found.get("name"));
                            ts.done();
                        }).onResult(data, thrown);
                        return true;
                    });
        });
    }

    @BeforeEach
    public void startup() throws IOException, InterruptedException, ExecutionException {
        System.setProperty("acteur.debug", "true"); // mongodb logging
        Settings settings = Settings.builder()
                .build();
        Dependencies deps = new Dependencies(settings, new MongoHarness.Module(),
                new LoggingModule().bindLogger("foo").bindLogger("bar"),
                new BunyanMongodbModule(settings)
        );
        foo = deps.getInstance(Key.get(Logs.class, Names.named("foo")));
        bar = deps.getInstance(Key.get(Logs.class, Names.named("bar")));
        records = deps.getInstance(
                Key.get(new TypeLiteral<MongoFutureCollection<Document>>() {
                }, Names.named(GUICE_BINDING_LOG_COLLECTION)));

        dlsink = deps.getInstance(Key.get(LogSink.class, Names.named(GUICE_BINDING_DEFAULT_SINK)));
        // Init the db
        records.count().get();
    }

    @AfterEach
    public void shutdown() throws Exception {
        if (deps != null) {
            deps.shutdown();
        }
    }
}
