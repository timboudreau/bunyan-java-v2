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
import com.google.common.collect.Streams;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
public class FolderLogStreamFactory implements StreamFactory {

    private final Path path;
    private final ObjectMapper mapper;
    private final Predicate<Path> fileFilter;
    private final boolean recursive;

    @Inject
    public FolderLogStreamFactory(Path path, ObjectMapper mapper, Predicate<Path> fileFilter, boolean recursive) {
        this.path = path;
        this.mapper = mapper;
        this.fileFilter = fileFilter;
        this.recursive = recursive;
    }

    private Stream<Path> paths() throws IOException {
        if (Files.isDirectory(path)) {
            if (recursive) {
                return Files.walk(path, FileVisitOption.FOLLOW_LINKS).filter(pth -> !Files.isDirectory(pth)).filter(fileFilter);
            } else {
                return Files.list(path).filter(pth -> !Files.isDirectory(pth)).filter(fileFilter);
            }
        } else {
            return Collections.singletonList(path).stream();
        }
    }

    private List<Path> sortedPaths() throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> str = paths()) {
            str.forEach(result::add);
        }
        result.sort((a, b) -> {
            String fa = a.getFileName().toString();
            String fb = b.getFileName().toString();
            return fa.compareTo(fb);
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    public Stream<LogRecord> stream() throws IOException {
        List<Stream<LogRecord>> streams = new ArrayList<>();
        List<Path> paths = sortedPaths();

        for (Path p : paths) {
            LogStreamFactory ls = new LogStreamFactory(p, mapper);
            streams.add(ls.stream());
        }

        return Streams.concat(streams.toArray(Stream[]::new));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Stream<T> convertedStream(Predicate<LogRecord> pred, Class<T> type) throws IOException {
        List<Stream<T>> streams = new ArrayList<>();
        List<Path> paths = sortedPaths();

        for (Path p : paths) {
            LogStreamFactory ls = new LogStreamFactory(p, mapper);
            streams.add(ls.convertedStream(pred, type));
        }
        return Streams.concat(streams.toArray(Stream[]::new));
    }
}
