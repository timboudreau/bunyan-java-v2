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
import static com.mastfrog.util.preconditions.Checks.greaterThanZero;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Tim Boudreau
 */
final class AsyncLogQueue implements Runnable {

    @JsonProperty("threadpool")
    private final ExecutorService svc;

    AsyncLogQueue() {
        this(3, Thread.currentThread().getPriority() - 1, true);
    }

    AsyncLogQueue(int threads, int priority, boolean useShutdownHook) {
        svc = new PoliteExecutorService(greaterThanZero("threads", threads),
                greaterThanZero("priority", priority));
        if (useShutdownHook) {
            Runtime.getRuntime().addShutdownHook(new Thread(this, "async-log-shutdown"));
        }
    }

    void run(Runnable toRun) {
        svc.submit(toRun);
    }

    void enqueue(LogSink orig, JSONContext ctx, Map<String, Object> logRecord) {
        if (svc.isShutdown()) {
            orig.push(ctx, logRecord);
            return;
        }
        svc.submit(() -> {
            orig.push(ctx, logRecord);
        });
    }

    boolean shutdown() {
        if (!svc.isShutdown()) {
            for (Runnable r : svc.shutdownNow()) {
                try {
                    r.run();
                } catch (Exception | Error e) {
                    LoggingLogging.log(e);
                }
            }
            return true;
        }
        return false;
    }

    void awaitExit() {
        if (!svc.isTerminated()) {
            try {
                svc.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                LoggingLogging.log("Interrupted awaiting termination", ex);
            }
        }
    }

    @Override
    public void run() {
        if (!svc.isShutdown()) {
            svc.shutdown();
        }
        awaitExit();
    }
}
