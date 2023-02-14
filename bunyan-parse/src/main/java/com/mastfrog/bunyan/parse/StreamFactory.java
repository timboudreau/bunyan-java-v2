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
package com.mastfrog.bunyan.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.jackson.configuration.JacksonConfigurer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 *
 * @author Tim Boudreau
 */
public interface StreamFactory {

    Stream<LogRecord> stream() throws IOException;

    <T> Stream<T> convertedStream(Predicate<LogRecord> pred, Class<T> type) throws IOException;

    default Stream<LogRecord> stream(Predicate<LogRecord> filter) throws IOException {
        return stream().filter(filter);
    }

    default void read(Predicate<LogRecord> filter, Function<LogRecord, ReadResult> reader) throws IOException {
        try (Stream<LogRecord> lines = stream(filter)) {
            lines.forEachOrdered((t) -> {
                ReadResult res = reader.apply(t);
                if (res != ReadResult.CONTINUE && res != null) {
                    throw new Abort();
                }
            });
        } catch (Abort abort) {
            //do nothing
        }
    }

    public static StreamFactory forPath(Path path) {
        return forPath(path, false);
    }

    public static StreamFactory forPath(Path path, boolean recursive) {
        return forPath(path, ".log", recursive);
    }

    public static StreamFactory forPath(Path path, String fileExt, boolean recursive) {
        return forPath(path, JacksonConfigurer.configureFromMetaInfServices(new ObjectMapper()), fileExt, recursive);
    }

    public static StreamFactory forPath(Path path, ObjectMapper mapper, String fileExt, boolean recursive) {
        return forPath(path, mapper, pth -> pth.getFileName().toString().toLowerCase().endsWith(fileExt.toLowerCase()), recursive);
    }

    public static StreamFactory forPath(Path path, ObjectMapper mapper, Predicate<Path> fileMatcher, boolean recursive) {
        if (!Files.exists(path)) {
            return new FolderLogStreamFactory(path, mapper, pth -> false, false);
        }
        if (Files.isDirectory(path)) {
            return new FolderLogStreamFactory(path, mapper, fileMatcher, recursive);
        } else {
            return new LogStreamFactory(path, mapper);
        }
    }
}
