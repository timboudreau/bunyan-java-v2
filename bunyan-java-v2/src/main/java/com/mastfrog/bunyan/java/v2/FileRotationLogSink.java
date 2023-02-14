/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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

import com.mastfrog.function.throwing.ThrowingRunnable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
final class FileRotationLogSink extends AbstractLogSink implements ThrowingRunnable {

    private static final Pattern SUFFIX_PATTERN = Pattern.compile("(.*?)_\\d+");
    private final Path basePath;
    private Path currPath;
    private AtomicReference<FileLogSink> sinkRef;
    private final AtomicInteger index = new AtomicInteger(1);
    private final long maxSize;
    private final AtomicInteger writes = new AtomicInteger(1);

    FileRotationLogSink(long maxSize, Path path, Supplier<LoggingConfig> config) {
        super(config);
        this.maxSize = maxSize;
        this.basePath = currPath = path;
        try {
            while (Files.exists(currPath) && Files.size(currPath) >= maxSize) {
                currPath = nextPath(currPath);
            }
        } catch (IOException ex) {
            LoggingLogging.log(ex);
        }
        sinkRef = new AtomicReference<>(new FileLogSink(currPath, config));
    }

    static String extension(Path p) {
        String s = p.getFileName().toString();
        int ix = s.lastIndexOf('.');
        if (ix > 0) {
            return s.substring(ix + 1, s.length());
        }
        return "";
    }

    static String baseNameSansExtension(Path p) {
        String s = p.getFileName().toString();
        int ix = s.lastIndexOf('.');
        if (ix > 0) {
            s = s.substring(0, ix);
        }
        Matcher m = SUFFIX_PATTERN.matcher(s);
        if (m.find()) {
            s = m.group(1);
        }

        return s;
    }

    Path nextPath(Path maybeUsedPath) {
        while (Files.exists(maybeUsedPath)) {
            String newName = baseNameSansExtension(maybeUsedPath);
            String newExt = extension(maybeUsedPath);
            maybeUsedPath = maybeUsedPath.getParent().resolve(newName + "_"
                    + index.getAndIncrement() + (newExt.length() > 0 ? ("." + newExt) : ""));
        }
        return maybeUsedPath;
    }

    FileLogSink sink() {
        return sinkRef.get();
    }

    @Override
    public void push(JSONContext ctx, Map<String, Object> logrecord) {
        sink().push(ctx, logrecord);
        if (writes.getAndIncrement() % 50 == 0) {
            checkNeedRotation();
        }
    }

    private void checkNeedRotation() {
        FileLogSink s = sinkRef.get();
        try {
            long currSize = s.size();
            if (currSize >= maxSize && s.isOpen()) {
                rotate(s);
            }
        } catch (IOException ex) {
            LoggingLogging.log(ex);
        }
    }

    private void rotate(FileLogSink sink) {
        Path next;
        synchronized (this) {
            next = currPath = nextPath(sink.path());
        }
        FileLogSink nue = new FileLogSink(next, configSupplier());
        sinkRef.compareAndSet(sink, nue);
        // Ensure any enqueued work referencing the old sink completes
        // before we close the old one - new writes will go to the new
        // sink from here.
        configSupplier().get().logQueue().run(() -> {
            try {
                sink.close();
            } catch (IOException ex) {
                LoggingLogging.log(ex);
            }
        });
    }

    @Override
    public void run() throws Exception {
        sink().run();
    }

    @Override
    public String toString() {
        String result = basePath.toString();
        Path cp;
        synchronized (this) {
            cp = currPath;
        }
        if (!cp.equals(basePath)) {
            result += "(" + cp.getFileName() + ")";
        }
        return result;
    }

}
