/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.MapBuilder2;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.AppendableCharSequence;
import com.mastfrog.util.time.TimeUtil;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
class LogImpl implements Log {

    @JsonProperty("name")
    final String name;
    @JsonProperty("level")
    LogLevel level;
    private final List<Object> m = new ArrayList<>(10);

    public static final String DEBUG = "debug";
    public static final String ERROR = "error";
    public static final String FATAL = "fatal";
    public static final String INFO = "info";
    public static final String TRACE = "trace";
    public static final String WARN = "warn";
    private final LoggingConfig config;
    private final JSONContext ctx;
    private final String oldCtx;

    @SuppressWarnings("LeakingThisInConstructor")
    LogImpl(String name, LogLevel level, LoggingConfig config) {
        this.ctx = new JSONContext(config);
        this.name = name;
        this.level = level;
        this.config = config;
        oldCtx = LogContext.enter(name);
    }

    public Log escalate(LogLevel newLevel) {
        if (notNull("newLevel", newLevel).intValue() > level.intValue()) {
            this.level = newLevel;
        }
        return this;
    }

    @Override
    public Log message(String msg) {
        m.add(AbstractSingletonMap.ofObject("msg", notNull("msg", msg)));
        return this;
    }

    private static LogLevel levelForThrowable(Object o) {
        Throwable t = (Throwable) o;
        if (t instanceof Error || t instanceof NullPointerException || t instanceof ThreadDeath) {
            return LogLevel.FATAL;
        }
        return LogLevel.ERROR;
    }

    @Override
    public Log add(Object object) {
        if (config.isEscalate() && object instanceof Throwable) {
            escalate(levelForThrowable(object));
        }
        m.add(notNull("object", object));
        if (!maybeCheckMap(object)) {
            ctx.check(object);
        }
        return this;
    }

    private boolean maybeCheckMap(Object o) {
        if (o instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!(e.getValue() instanceof AbstractSingletonMap)) {
                    ctx.check(e.getValue());
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public Log add(String name, int value) {
        m.add(AbstractSingletonMap.ofInt(name, value));
        return this;
    }

    @Override
    public Log add(String name, long value) {
        m.add(AbstractSingletonMap.ofLong(name, value));
        return this;
    }

    @Override
    public Log add(String name, boolean value) {
        m.add(AbstractSingletonMap.ofBoolean(name, value));
        return this;
    }

    @Override
    public Log add(String name, Object value) {
        if (!maybeCheckMap(value)) {
            ctx.check(value);
        }
        if (config.isEscalate() && value instanceof Throwable) {
            escalate(levelForThrowable(value));
        }
        m.add(AbstractSingletonMap.ofObject(notNull("name", name), value));
        return this;
    }

    @Override
    public Log add(Throwable error) {
        if (config.isEscalate()) {
            escalate(levelForThrowable(error));
        }
        m.add(AbstractSingletonMap.ofObject("error", notNull("error", error)));
        return this;
    }

    @Override
    public Log addLazy(String key, Supplier<Object> val) {
        m.add(AbstractSingletonMap.lazy(key, val));
        return this;
    }

    static String formattedNow() {
        ZonedDateTime now = ZonedDateTime.now().withZoneSameInstant(TimeUtil.GMT);
        return TimeUtil.toIsoFormat(now);
    }

    static int pid = -1;

    static int pid() {
        if (pid != -1) {
            // Getting the pid is a high-cost call if a bunch of threads
            // wind up locked in InetAddress.getLocalHost().
            return pid;
        }
        String beanName = ManagementFactory.getRuntimeMXBean().getName();
        try {
            return pid = Integer.parseInt(beanName.split("@")[0]);
        } catch (NumberFormatException nfe) {
            return pid = 0;
        }
    }

    @Override
    public void close() {
        LogContext.exitTo(oldCtx);
        ctx.clear();
        if (!config.isEnabled(name, level)) {
            return;
        }
        config.decorate(this);
        LogSink sink = config.sinkFor(name, level);
        AppendableCharSequence msg = new AppendableCharSequence(60);
        MapBuilder2<String, Object> mb = CollectionUtils.map();
        for (Iterator<Object> it = m.iterator(); it.hasNext();) {
            Object o = it.next();
            CharSequence s = null;
            if (o == null) {
                continue;
            } else if (o instanceof CharSequence) {
                s = (CharSequence) o;
                it.remove();
            } else if (o instanceof Boolean || o instanceof Number) {
                s = o.toString();
            } else if (o instanceof Map<?, ?>) {
                Map<?, ?> m = ((Map<?, ?>) o);
                if (m instanceof AbstractSingletonMap) {
                    AbstractSingletonMap am = (AbstractSingletonMap) m;
                    String key = am.key();
                    Object val = am.getValue();
                    if ("msg".equals(key)) {
                        if (val instanceof CharSequence) {
                            s = (CharSequence) val;
                        } else {
                            mb.map("_msg").to(val);
                        }
                    } else {
                        mb.map(key).to(val);
                    }
                } else {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if ("msg".equals(e.getKey())) {
                            Object ob = e.getValue();
                            if (ob instanceof CharSequence) {
                                s = (CharSequence) ob;
                            } else {
                                mb.map("_msg").to(ob);
                            }
                        } else {
                            Object key = e.getKey();
                            mb.map(Objects.toString(key)).to(e.getValue());
                        }
                    }
                }
                it.remove();
            } else if (o instanceof List<?>) {
                List<?> l = (List<?>) o;
                int sz = l.size();
                for (int i = 0; i < sz; i++) {
                    mb.map(Integer.toString(i)).to(l.get(i));
                }
            } else {
                try {
                    Map<Object, Object> mm = CollectionUtils.uncheckedMap(config._mapper().readValue(ctx.writeValueAsBytes(o), Map.class));
                    for (Map.Entry<?, ?> e : mm.entrySet()) {
                        mb.map(Objects.toString(e.getKey())).to(e.getValue());
                    }
                } catch (IOException ex) {
                    LoggingLogging.log("Exception in map conversion", ex, true);
                }
            }
            if (o instanceof Map<?, ?>) {
                Map<?, ?> m = (Map) o;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!"msg".equals(e.getKey())) {
                        mb.map(Objects.toString(e.getKey())).to(e.getValue());
                    }
                }
            }
            if (s != null && !Strings.charSequenceContains(msg, s, false)) {
                if (msg.length() != 0) {
                    msg.append(' ');
                }
                msg.append(s);
            }
        }
        String hn = config.hostname();
        if (hn == null) {
            hn = hostname();
        }
        mb.map("name").to(name)
                .map("msg").to(msg.toString())
                .map("v").to(0)
                .map("time").to(formattedNow())
                .map("pid").to(pid())
                .map("level").to(level.intValue())
                .map("hostname").to(hn);
        sink.push(ctx, mb.build());
        m.clear();
    }

    @Override
    public Log addIfNotNull(String name, Object value) {
        if (value != null) {
            return add(name, value);
        }
        return this;
    }

    @Override
    public String toString() {
        return name + "(" + level + ")";
    }

    private static String foundHostName;

    static String hostname() {
        if (foundHostName != null) {
            return foundHostName;
        }
        String hostname = System.getProperty(LoggingConfig.PROP_HOSTNAME);
        if (hostname != null) {
            return foundHostName = hostname;
        }
        hostname = System.getenv("HOSTNAME");
        if (hostname != null) {
            return foundHostName = hostname;
        }
        try {
            return foundHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            try {
                return findHostnameFromNetworkAddress();
            } catch (SocketException ex) {
                return "localhost";
            }
        }
    }

    static String findIpV4Address(NetworkInterface iface) {
        // XXX this is dangerous if we need ipv6 - but it is only called
        // if the ingest server's url host is "localhost"
        String result = null;
        for (InetAddress addr : CollectionUtils.toIterable(iface.getInetAddresses())) {
            String hn = addr.getHostAddress();
            if (!hn.contains(":")) {
                result = hn;
            }
        }
        return result;
    }

    static String findHostnameFromNetworkAddress() throws SocketException {
        // For localhost urls (demo vm), try to find an IPv4 address - localhost definitely
        // won't work, and we need something that is an external interface
        // Preferr IPv4 since that is usually what you get
        String result = null;
        String preferred = null;
        if (preferred != null) {
            if (NetworkInterface.getByName(preferred) == null) {
                preferred = null;
            }
        }
        result = "localhost";
        NetworkInterface bestMatch = null;
        for (NetworkInterface ni : CollectionUtils.<NetworkInterface>toIterable(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isLoopback()) {
                if (ni.isUp()) {
                    if (!ni.isVirtual() && !ni.isPointToPoint()) {
                        if (preferred != null && preferred.equalsIgnoreCase(ni.getName()) && ni.getInetAddresses().hasMoreElements()) {
                            String addr = findIpV4Address(ni);
                            if (addr != null) {
                                result = addr;
                            }
                            bestMatch = ni;
                            break;
                        } else {
                            bestMatch = ni;
                        }
                    }
                }
            }
        }
        if (bestMatch != null && "localhost".equals(result)) {
            String check = findIpV4Address(bestMatch);
            if (check != null) {
                return check;
            }
        }
        return result;
    }
}
