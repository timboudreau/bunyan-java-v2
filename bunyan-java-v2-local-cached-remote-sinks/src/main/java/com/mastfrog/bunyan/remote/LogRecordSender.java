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

package com.mastfrog.bunyan.remote;

import com.mastfrog.logstructured.LogStructuredReader;
import com.mastfrog.logstructured.UnadvancedRead;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
final class LogRecordSender implements Runnable {

    private final LogStructuredReader<Map<String, Object>> reader;
    private final RemoteSink sink;
    private final AtomicBoolean sendInProgress = new AtomicBoolean();
    private final ExecutorService threadPool;
    private final AtomicLong touches = new AtomicLong(Long.MIN_VALUE);

    LogRecordSender(LogStructuredReader<Map<String, Object>> reader, RemoteSink sink, ExecutorService threadPool) {
        this.reader = reader;
        this.sink = sink;
        this.threadPool = threadPool;
    }

    void touch() {
        boolean tps = threadPool.isShutdown();
        if (!tps) {
            threadPool.submit(this);
        }
        touches.getAndIncrement();
    }

    @Override
    public void run() {
        try {
            long lastTouches = touches.get();
            UnadvancedRead<Map<String, Object>> uar = reader.read();
            if (uar == null) {
                for (int i = 0; i < 5; i++) {
                    Thread.sleep(20);
                    uar = reader.read();
                }
            }
            if (uar != null) {
                sendInProgress.set(true);
                //                    JSONContext ctx = new JSONContext(config);
                //                    sink.push(ctx, uar.get(), () -> {
                UnadvancedRead<Map<String, Object>> u = uar;
                sink.push(null, uar.get(), () -> {
                    sendInProgress.set(false);
                    try {
                        u.advance();
                        if (!threadPool.isShutdown() && (reader.hasUnread() || touches.get() > lastTouches)) {
                            threadPool.submit(this);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(RemoteSinkSupport.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            }
        } catch (IOException ex) {
            Logger.getLogger(RemoteSinkSupport.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(RemoteSinkSupport.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
