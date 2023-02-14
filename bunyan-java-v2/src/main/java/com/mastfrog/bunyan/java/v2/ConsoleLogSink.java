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

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
final class ConsoleLogSink extends AbstractLogSink {

    private final boolean disabled;
    private final Consumer<? super CharSequence> output;

    ConsoleLogSink(Supplier<LoggingConfig> configSupplier, Consumer<? super CharSequence> output) {
        super(configSupplier);
        disabled = Boolean.getBoolean("disable.console.logger"); // for tests
        this.output = output;

    }

    ConsoleLogSink(Supplier<LoggingConfig> configSupplier) {
        this(configSupplier, System.out::println);
    }

    @Override
    @JsonValue
    public String toString() {
        return "console";
    }

    @Override
    public void push(JSONContext ctx, Map<String, Object> logrecord) {
        if (disabled) {
            return;
        }
        try {
            CharSequence result = ctx.writeValueAsString(logrecord);
            assert result != null : ctx + " returned null for " + logrecord;
            output.accept(result);
        } catch (Exception ex) {
            LoggingLogging.log("Exception writing json", ex, true);
        }
    }
}
