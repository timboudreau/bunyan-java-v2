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

import com.mastfrog.bunyan.java.v2.JSONContext;
import com.mastfrog.bunyan.java.v2.LogSink;
import com.mastfrog.logstructured.LogStructuredAppender;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
class DiskSink implements LogSink {

    private final LogStructuredAppender<Map<String, Object>> appender;
    Runnable touch;

    public DiskSink(LogStructuredAppender<Map<String, Object>> appender) {
        this.appender = appender;
    }

    @Override
    public void push(JSONContext ctx, Map<String, Object> logrecord) {
        try {
            appender.append(logrecord);
        } catch (IOException ex) {
            Logger.getLogger(RemoteSinkSupport.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (touch != null) {
                touch.run();
            }
        }
    }

}
