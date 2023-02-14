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

import static com.mastfrog.bunyan.java.v2.LoggingConfig.JsonSerializationPolicy.ALWAYS_JACKSON;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.JsonSerializationPolicy.NEVER_JACKSON;
import com.mastfrog.util.fileformat.SimpleJSON;
import static com.mastfrog.util.fileformat.SimpleJSON.Style.COMPACT;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author Tim Boudreau
 */
public class JSONContext implements JSONEncoder {

    private final LoggingConfig config;
    @SuppressWarnings("VolatileArrayField")
    private volatile byte[] cachedBytes;
    private volatile String cachedString;
    private boolean simpleJsonSafe = true;

    public JSONContext(LoggingConfig config) {
        this.config = config;
        simpleJsonSafe = config.serializationPolicy() != ALWAYS_JACKSON;
    }

    void clear() {
        cachedString = null;
        cachedBytes = null;
    }

    <T> T check(T o) {
        if (simpleJsonSafe) {
            if (config.serializationPolicy() == NEVER_JACKSON) {
                return o;
            }
            simpleJsonSafe = SimpleJSON.canDefinitelySerialize(o);
        }
        return o;
    }

    @Override
    public CharSequence writeValueAsString(Object o) throws IOException {
        CharSequence result = cachedString;
        if (cachedString == null) {
            byte[] bytes = cachedBytes;
            if (bytes == null) {
                bytes = cachedBytes = writeValueAsBytes(o);
            }
            result = cachedString;
            if (result == null) {
                result = cachedString = new String(bytes, UTF_8);
            }
            assert result != null;
        }
        return result;
    }

    @Override
    public byte[] writeValueAsBytes(Object o) throws IOException {
        byte[] result = cachedBytes;
        if (result != null) {
            return result;
        }
        if (simpleJsonSafe) {
            cachedString = SimpleJSON.stringify(o, COMPACT);
            result = cachedString.getBytes(UTF_8);
        } else {
            result = config._mapper().writeValueAsBytes(o);
        }
        assert result != null : "got null";
        return cachedBytes = result;
    }
}
