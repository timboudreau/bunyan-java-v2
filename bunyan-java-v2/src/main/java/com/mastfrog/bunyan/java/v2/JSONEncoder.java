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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.util.fileformat.SimpleJSON;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author Tim Boudreau
 */
public interface JSONEncoder {

    default byte[] writeValueAsBytes(Object o) throws IOException {
        return writeValueAsString(o).toString().getBytes(UTF_8);
    }

    public CharSequence writeValueAsString(Object o) throws IOException;

    public static JSONEncoder SIMPLE = (Object o) -> {
        return SimpleJSON.stringify(o, SimpleJSON.Style.COMPACT);
    };

    public static JSONEncoder jackson(ObjectMapper mapper) {
        return new JSONEncoder() {
            @Override
            public CharSequence writeValueAsString(Object o) throws IOException {
                return mapper.writeValueAsString(o);
            }

            @Override
            public byte[] writeValueAsBytes(Object o) throws IOException {
                return mapper.writeValueAsBytes(o);
            }
        };
    }
}
