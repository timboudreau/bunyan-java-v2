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
import com.mastfrog.util.collections.AtomicLinkedQueue;
import static com.mastfrog.util.preconditions.Checks.greaterThanZero;
import com.mastfrog.util.thread.OneThreadLatch;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An executor service which uses entirely non-blocking, atomic data structures,
 * and which switches to executing tasks synchronously when shut down.
 * Implements the ExecutorService API only insofar as is needed for this
 * application - does not return futures, and does not implement the more
 * esoteric methods such as invokeAny().
 * <p>
 * This class exists mainly to implement correct shutdown behavior for a use
 * case where failing to run any submitted job is absolutely unacceptable.
 * </p>
 *
 * @author Tim Boudreau
 */
class PoliteExecutorService implements ExecutorService {

    @JsonIgnore
    private final AtomicLinkedQueue<Runnable> q = new AtomicLinkedQueue<>();
    @JsonIgnore
    private final AtomicBoolean shutdown = new AtomicBoolean();
    @JsonIgnore
    private final OneThreadLatch latch = new OneThreadLatch();
    @JsonIgnore
    private final CountDownLatch allThreadsExitedLatch;
    @JsonProperty("threads")
    private final int threads;

    PoliteExecutorService(int threads, int priority) {
        this.threads = greaterThanZero("threads", threads);
        allThreadsExitedLatch = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(this::run, getClass().getSimpleName());
            t.setDaemon(true);
            t.setName("PoliteExecutorService-" + i);
            t.setPriority(priority);
            t.start();
        }
    }

    @JsonProperty("pending")
    int pendingJobs() {
        return q.size();
    }

    void awaitJob() throws InterruptedException {
        // wait for the resettable latch to be triggered
        latch.await();
        // then reset it for the next loop
//        latch.reset(1);
    }

    void run() {
        try {
            for (;;) {
                if (shutdown.get()) {
                    return;
                }
                try {
                    awaitJob();
                } catch (InterruptedException ex) {
                    LoggingLogging.log(Thread.currentThread() + "interrupted awaiting job", ex);
                }
                for (Runnable r : q.drain()) {
                    runOneSync(r);
                }
            }
        } finally {
            allThreadsExitedLatch.countDown();
        }
    }

    @Override
    public void shutdown() {
        // On shutdown, run anything pending
        List<Runnable> pending = shutdownNow();
        for (Runnable r : pending) {
            runOneSync(r);
        }
    }

    private void runOneSync(Runnable task) {
        try {
            task.run();
        } catch (Exception ex) {
            LoggingLogging.log("Exception in " + task, ex, true);
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        if (shutdown.compareAndSet(false, true)) {
            // Spin until all threads have executed, repeatedly
            // triggering the latch they may be waiting on
            while (allThreadsExitedLatch.getCount() > 0) {
                for (int i = 0; i < threads; i++) {
                    latch.disable();
                    try {
                        // Give a thread a chance to wake up and finish
                        Thread.currentThread().sleep(10);
                    } catch (InterruptedException ex) {
                        LoggingLogging.log("Interrupted in shutdown", ex, false);
                    }
//                    latch.reset(i == threads - 1 ? 0 : 1);
                    latch.releaseOne();
                }
            }
            // Be sure all running threads have cleared the exit latch
            try {
                allThreadsExitedLatch.await();
            } catch (InterruptedException ex) {
                LoggingLogging.log("Interrupted in shutdown waiting for all threads to exit", ex, false);
            }
            // Drain the queue
            List<Runnable> result = q.drain();
            do {
                // And spin some more in case any thread was close enough to
                // adding to the queue that it succeeded - wait out any races
                try {
                    result.addAll(q.drain());
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    LoggingLogging.log("Interrupted in sleep while draining queue", ex, false);
                }
            } while (!q.isEmpty());
            return result;
        }
        return Collections.emptyList();
    }

    @Override
    @JsonProperty("shutdown")
    public boolean isShutdown() {
        return shutdown.get();
    }

    @Override
    @JsonProperty("terminated")
    public boolean isTerminated() {
        return isShutdown() && latch.releaseCount() < 0;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        latch.await(timeout, unit);
        return isTerminated();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        submit(() -> {
            try {
                task.call();
            } catch (Exception e) {
                LoggingLogging.log("Exception invoking " + task, e, true);;
            }
        });
        return null;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        submit(task);
        return null;
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (isShutdown()) {
            runOneSync(task);
        } else {
            q.add(task);
            latch.releaseOne();
        }
        return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        for (Callable<T> c : tasks) {
            submit(c);
        }
        return Collections.emptyList();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void execute(Runnable command) {
        submit(command);
    }
}
