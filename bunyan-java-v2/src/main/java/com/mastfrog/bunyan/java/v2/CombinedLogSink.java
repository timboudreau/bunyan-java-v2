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
import com.mastfrog.abstractions.Wrapper;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
final class CombinedLogSink implements LogSink, Wrapper<LogSink[]> {

    @JsonProperty(value = "a")
    private final LogSink b;
    @JsonProperty(value = "b")
    private final LogSink a;

    public CombinedLogSink(LogSink a, LogSink b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public void push(JSONContext ctx, Map<String, Object> logrecord) {
        try {
            a.push(ctx, logrecord);
        } finally {
            b.push(ctx, logrecord);
        }
    }

    @Override
    public String toString() {
        return a + " & " + b;
    }

    static LogSink deAsync(LogSink what) {
        if (what instanceof CombinedLogSink) {
            return ((CombinedLogSink) what).deAsync();
        }
        return what;
    }

    CombinedLogSink deAsync() {
        LogSink aa = AsyncLogSink.unwrap(a);
        LogSink bb = AsyncLogSink.unwrap(b);
        return aa == a && bb == b ? this : new CombinedLogSink(aa, bb);
    }

    //        @Override
    //        public LogSink async() {
    //            LogSink aAsync = a instanceof AsyncLogSink ? ((AsyncLogSink) a).orig
    //                    : a;
    //            LogSink bAsync = a instanceof AsyncLogSink ? ((AsyncLogSink) b).orig
    //                    :b;
    //            return new AsyncLogSink(configSupplier, new CombinedLogSink(aAsync, bAsync));
    //        }
    @Override
    public LogSink[] wrapped() {
        CombinedLogSink un = deAsync();
        return new LogSink[]{a, b};
    }
}
