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

import java.util.function.Supplier;

/**
 * A builder for a single log record, which can have objects (which will be
 * dissected as JSON fields included in the log record - with the caveat that
 * JSON fields whose name is reserved by Bunyan's logging spec [time, hostname,
 * v, message] will get _ prefixed), key value pairs or strings added to it, and
 * which will be written when close() is called. Implements AutoCloseable so
 * try-with-resources style invocation of logging can be performed, e. g.
 * <pre>
 * try (Log log = LOGS.info("starting-server")) {
 *      log.add("address", theServer.localAddress())
 *          .add("ssl", theServer.isSSL())
 *          .add("dataDir", theServer.dataDir());
 *      try {
 *          server.start();
 *      } catch (IOException ioe) {
 *          log.add(ioe);
 *      ]
 * }
 * </pre>
 * <p>
 * Note: Logging may be asynchronous, in which case the order lines are logged
 * is not necessarily the order lines are output in. Atomic sequence numbers can
 * be set up in LoggingConfig if that is helpful.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface Log extends AutoCloseable {

    /**
     * Set the message for this log record. Additional strings passed to this
     * method or add(Object) will be concatenated to this string.
     *
     * @param msg
     * @return this
     */
    public Log message(String msg);

    /**
     * Add some object to the log record. If it is serializable by Jackson, its
     * key/value pairs will be incorporated into this log record; if it is a
     * list, it will be turned into a Map&lt;Integer,Object&gt; and the same
     * done with it; if it is a string, it will be concatenated with the already
     * set message (if any) or will become the message.
     *
     * @param o An object
     * @return this
     */
    public Log add(Object o);

    /**
     * Add a key value pair to this log record
     *
     * @param name The key
     * @param value the value
     * @return this
     */
    public Log add(String name, Object value);

    public Log add(String name, int value);

    public Log add(String name, long value);

    public Log add(String name, boolean value);

    public Log addLazy(String name, Supplier<Object> value);

    public Log addIfNotNull(String name, Object value);

    /**
     * Add an error. The error's message becomes the message of this log record;
     * its stack is serialized as JSON.
     *
     * @param t A throwable
     * @return this
     */
    public Log add(Throwable t);

    /**
     * Write out this log record
     */
    @Override
    public void close();

}
