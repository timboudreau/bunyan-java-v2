Bunyan Java (v2)
================

This is the revised and simplified port of NodeJS's Bunyan logger to Java, with
Java specific-idioms.  The new revision is a mostly-compatible replacement for
[the original](https://github.com/timboudreau/bunyan-java), with the following differences:

 * Log levels not exposed in the API other than via method names
 * No dependency on Guice, and minimal other dependencies (the adjacent project
 `giulius-bunyan-java-v2` handles that)
 * Can use Jackson, or lighter-weight JSON generation in the case that the 
types logged are simple, and can adaptively decide which to use based on 
log record contents if desired
 * Configuration can be done with system properties or a properties file
 * Routing to multiple log files is easy
 * Additional routing of high-severity records is possible
 * Log levels configurable on a per-logger basis without programmatic intervention
 * Static logger instances are possible - use `Logs.named(logName)` to create
them; by default, the first LoggingConfig created becomes the default system
logging config (unless you tell it not to); if the number of records logged
(and cached) before a LoggingConfig exists passes a configurable threshold, the
system will self-initialize via system properties (you should always configure
one - this is a failover system, not the desired way to use it, and logging
will be delayed on startup if you don't)
 * Easy to write alternate log sinks which, say, write to a remote MongoDB 
instance or similar
 * Configuration can be programmatic, using `LoggingConfig.Builder` or defined
declaratively using properties

The whole idea is to be able to consolidate logs from multiple machines, and
still cleanly differentiate which came from where, and thus have an overview of
an entire distributed system.  The utilities that come with Bunyan make it
easy to view, filter and process a stream of Bunyan log data from the console.

Logged lines are simply single-line JSON - for example:
```json
{"dur":6,"msg":"request","agent":"curl/7.33.0","address":"0:0:0:0:0:0:0:1","method":"GET","level":20,"pid":46955,"path":"plugin/repository/everything/com/mastfrog/blather/2.3.2/blather-2.3.2.pom","hostname":"localhost","v":0,"host":"localhost:5956","name":"requests","id":"doama:1","time":"2018-03-05T00:07:19.020Z","status":404}
```

Design
======

Bunyan-Java is designed around the idea of building up log records as you go - 
a log record is effectively a `Map` with some default values and values you
add to it as your code proceeds.  You get a builder for a log record from a
`Logs` instance, and call `close()` on it when it is complete.  Both lambda-
and try-with-resources calling patterns are supported.

You get a `Logs` instance - a factory for `Log` records, and the equivalent of a
`Logger` in other frameworks, either statically from `Logs.named("someName")` or
from a `LoggingConfig` you pass into your code (or injected by Guice if you are
using `giulius-bunyan-v2`):

```java
public class MyClass {
   private static final Logs LOGS = Logs.named("my-class");

   public Response handleRequest(User user, Request request) throws Exception {
       LOGS.debug("request", log -> {
           log.add("user", user.id()).add("req", request);
           if (!request.thing().exists()) {
               // This exception will be automatically included in the log record
               throw new MyException("Oh no!", req.thing());
           }
           return doSomething(req);
       }); // Log record gets written here, and if an exception 
           // was thrown, may be written at error level, not debug, if the
           // escalation feature is enabled
   }
```

API
===

The API for code that wants to log is idiosyncratic but straightforward once
you get the idea:  You are logging JSON key/value pairs.  Each record has a
string *message*, a *time* a *log name*, and contains a
few other fields that the Bunyan NodeJS library defines - *v* for format
version, *hostname* for the host name, and *pid* for the process PID.

You typically start by creating a `LoggingConfig` using `LoggingConfig.builder()`,
early in application startup.  Unless you call `nonDefault()` on the builder, it
will become the logging config used by any statically defined loggers.

To define a logger, simply use `Logs.named(someName)`, e.g.

```java
public class MyServer {
    private static final Logs REQUEST_LOGS = Logs.named("request");
    ...
}
```

Since a JSON log record is not a single line of text, but has <i>contents</i>,
it is typical to create one at the start of a task, and add items to it
as you go.  `Log`, the builder for log records, implements Java's `AutoCloseable`
interface, so the natural pattern is to use Java's _try-with-resources_ pattern,
e.g.

```java
public File lookupFile(Request request) {
    try (Log log = REQUEST_LOGS.info("file-requested")) {
        log.add("filename", request.getFileName())
            .add("by", request.remoteAddress());
        if (Files.exists(dir.resolve(request.getFileName())) {
            log.add("exists", true);
            ...
        } else {
            log.add("exists", false);
            ...
        }
    } // this is where the log file gets written out
}
```

### Closure-based logging

Logging can also use lambdas, either throwing- or non-throwing.  This has the
advantage that any thrown exception is included in the log record (it will
be rethrown to the caller without any wrapping, thanks to 
[fiendish generic tricks](https://timboudreau.com/blog/Unchecked_checked_exceptions/read)).
You can pass a `Consumer<Log>`, or a `Function<Log,R>` (in which case the
computed value from the function will be returned), with throwing variants
(method names prefixed with `t` - e.g. `tinfo()`, `tdebug()`, etc. - otherwise
the compiler would force you to cast every usage as either a `Consumer` or
`ThrowingLogConsumer`, which would get annoying quickly).

That pattern looks like:

```java
static final Logs LOGS = Logs.named("files");
final Path theDir = ...;
public File createNewFile(String name) throws IOException {
    return LOGS.tinfo("create-file", logrecord -> {
        Path path = theDir.resolve(name);
        Files.createNewFile(path);
        logrecord.add("path", path);
        return path;
    });
}
```

If an IOException is thrown within the body of the `ThrowingLogFunction` (the
lambda above), it is automatically attached to the log record (the caller
will still need to catch it, but does not need to _log_ it - that is done).


SPI
===

The service provider interface for implementing your own log _destinations_ is
the `LogSink` class - simply implement this and configure your logging configuration
to log what you want there.  `LogSink` has a single method to implement,
 `push(JSONContext ctx, Map<String,Object> logrecord)`.


Features
========

### Contextual logging

Particularly with asynchronous programming, you may have generic error handling
code which needs to be able to log exceptions, but you don't want those exceptions
to just be logged under the category "errors" if we can do better.  For such a
situation, we have `Logs.contextual()`:  If a `Log` instance is unfinished in the
current thread, the name of that logger will be used, and if not you get the 
default.  For example:

```java
class GenericErrorHandler {

   private static final Logs logs = Logs.named("errors");

   static void handleError(Throwable thrown) {
       logs.contextual().error(thrown.getMessage()).add(thrown).close();
   }
}
```

### Escalation on Exceptions

It is entirely possible to be logging an ordinary operation which might or might
not throw an exception;  you have captured complete information about the
operation in a `Log` instance already.  If the `LoggingConfig` feature `escalateOnErrors()`
is set, if a `Throwable` is added to the log record, the existing `Log` instance's
error level will be escalated as follows:

 * If the throwable not an instanceof `Exception` (i.e. `Error`, `ThreadDeath` or something
else that suggests something has gone horrifically wrong with the runtime), or if
the error indicates obvious programmer-error (`NullPointerException` and friends),
then the level is escalated to **fatal**
 * Otherwise the level is escalated to **error**
 * If the level was greater than that which it would be escalated to already, it
is not changed

### Child Logs

A feature of Bunyan's which we support is _child loggers_ - a `Logs` instance
has several `createChild` methods, which lets you create a child `Logs` instance
whose created records will always contain whatever you pass to `createChild` -
so, for example, if you assign each request to a web server a unique ID, you
can simply call `REQUEST_LOGS.createChild("id", request.id())`, and pass that
to anything that processes the request - so you can trace the lifecycle of
a particular request, without anything needing to be configured to share that
information in log records the same way.

For basic logging, that really is it - two classes, `Logs` and `Log`.


Configurable Features
---------------------

The following are settable via system properties or methods on `LoggingConfig.Builder`
(note that if you are using `giulius-bunyan-java-v2`, the `Settings` properties
are _not the same_ and are compatible with v1 - see the constants on `LoggingModule`),
and are used if either the logging configuration is created from system properties,
or by a call to `LoggingConfig.fromProperties()`:


 * `bunyan-v2-logging-config-file` - A path to a `.properties` file which should be read and used in place of system properties for the logging configuration
 * `bunyan-v2-log-async` - Use asynchronous logging, so the thread doing the logging is not potentially blocked in I/O - the actual flushing of log records happens on a background thread (except during shutdown, when writes become synchronous so as not to lose log records).  In general, this is quite reliable, and VM shutdown hooks are used to ensure any pending log records are flushed before exit.  Of course a hard powerdown or `kill -9` does not allow exit hooks to run, but it is equally possible to lose synchronous log records under those circumstances.  Note that asynchronous logging _may_ result in log records being written out-of-order, particularly if multiple threads are used - hence the optional sequence number feature.
 * `bunyan-v2-log-async-threads` - The number of threads to use for asynchronous logging.
 * `bunyan-v2-async-log-thread-priority` - Set the thread priority for background logging threads
 * `bunyan-v2-default-log-file` - The log file to write to unless another one is specified for the logger being used
 * `bunyan-v2-severe-log-file` - If set, also log messages with level `error` or `fatal` to this file
 * `bunyan-v2-level` - The default log level to log at - any log records below this level are discarded with minimal overhead
 * `bunyan-v2-log-callers` - Attempt to include the source file, class, method and line number in log records.  This is **expensive**, unwinding the VM's stack on every logging invocation, and should not be done in production 
 * `bunyan-v2-seq-numbers` - Include a sequence number in each log record - useful with asynchronous logging to sort log records.  Leave off unless you are using asynchronous logging and it is really going to create confusion - sequence numbers are only really needed to differentiate log records written at _exactly the same millisecond_.
 * `bunyan-v2-log-console` - The default logging output is the console, unless you specify a log file.  If you specify a log file, console logging is turned off.  This property, when set to true, specifies to log to the console _in addition to_ any log file specified.
 * `bunyan-v2-routed-loggers` - Specify a list of logger names which will be routed to destination other than the default logging destination.  This is used in conjunction with
    * `bunyan-v2-route-level.$LOGGER_NAME` - Specify a different default logging level for a particular logger
 * `hostname` - The host name to use in log records, to avoid looking it up via `InetAdress.getLocalHost()` and friends.  The environment variable `HOSTNAME` is also checked.
 * `bunyan-v2-autoconfig-threshold` - A positive integer.  In the case of configuration from system properties, the number of pending log records to accept and cache if no logging config has been created, before deciding to auto-configure from system properties.  This allows static `Logs` instances created by `Logs.named(name)` to be logged to early in application startup, and get flushed once logging is configured.  Note that if the system property `bunyan-v2-logging-config-file` is set, logging configuration is initialized on the first record logged.
 * `bunyan-v4-json-policy` - Determines whether the lightweight (in terms of memory) JSON serializer or Jackson or both are used.  Possible values:
   * `adaptive` - Use lightweight JSON if the log record contains only simple types (java primitives, common and simple JDK types such as file paths, and network addresses, locales, time zones strings and character sequences, or maps, lists or arrays of the same)
   * `always-jackson` - Always use Jackson for JSON serialization
   * `never-jackson` - Always use the lightweight JSON serializer.  Note this may result in some JSON elements being logged as whatever `toString()` returns.
 * `bunyan-v2-logging-config-policy` - determines the behavior of logging auto-configuration, and what happens by default if a new `LoggingConfig` is created and a global one has not been set for use by statically defined `Logs` instances.  One of:
   * `non-default` - Creation of a `LoggingConfig` should _never_ result in it automatically setting itself as the global config
   * `set-if-unset` - Creation of a `LoggingConfig` should result in it setting itself as the global config if none has been configured (the default)
   * `take-over` - Creation of a `LoggingConfig` should _always_ replace the global config (useful mainly for tests)
 * `bunyan-v2-logging-escalate-errors` - A log record created with some lower log level such as `debug` or `trace` should escalate its level to `error` if a `Throwable` is added to it - this is useful to avoid the need to create a secondary log record to log exceptions if you already have one you are adding elements to.


### Asynchronous logging

By default, logging is synchronous, and both writing to a file and logging to the
system console are blocking operations.  For maximum throughput, you can configure
asynchronous logging.   The system will use a Java runtime shutdown hook to guarantee
as best it can that all pending log records are written out before the process exits.
This will succeed for anything but the process being brutally killed, or the filesystem
the logs are on being ripped out from under it, or something like that (note that
most OS-level init systems try to politely kill an application, and if it is still
running after some number of seconds, then send SIGTERM to it - there is _nothing_
an application can do about that).  Asynchronous logging does mean log records can
be written out of order - to mitigate this, you can optionally have the system assign
monotonic, atomic sequence numbers to each log record, which can assist (if timestamps
aren't enough) in disambiguating written-before / written-after.  Note that both the 
timestamp and the sequence number are generated when the log record is _closed_, not
when it is created.

### Routing Log Names and Severities To Files

Specific log names can be routed to specific files (note this removes them from the
default log and console logging);  and `error` and `fatal` level log records can
additionally be routed to specific files.  Of course, you can always implement
`LogSink` yourself to route things any way you want.

### Sequence Numbers

As noted under _asynchronous logging_, you can assign sequence numbers incrementing
atomically with each record.

### Caller Logging

The caller can be logged if desired - logging the class, file name, method and line
number which created a log record.  Note that:

 * The cost of unwinding the Java stack depends on its depth and is never, 
ever, free - you should use this when debugging an application, not in production

 * The caller that is logged will be the first caller on the stack that does not
share the same package as this library, and does not start with "java".  This is
accurate, but when code is invoked by third party libraries, not always intuitive.

### Log Decorators

If some information should, application-wide, always be in every log record, beyond
what is there by default, you can add one or more `Consumer<LogRecord>` instances
to add whatever you want to every record logged.

### Shutting Down The Logging System

`LoggingConfig` has a shutdown method, which will shut down and flush any pending
asynchronous log records, and if the configuration in question was set as the
global logging config for static instances, will reset all live Logs instances
defined via Logs.named() to their default state.

This is useful for running unit tests where logs may be examined, to simulate system
shutdown and exit before examining log records.

Adjacent Projects and Custom Log Sinks
======================================

The `LogSink` interface is quite simple, with only one method:

```
void push(JSONContext ctx, Map<String, Object> logrecord);
```

and can be configured in a `LoggingConfig.Builder` quite easily, with multiple
instances for different log names.

Two subprojects in the repository may be useful for implementations such
as remote logging:

 * `bunyan-java-v2-local-cached-remote-sinks` - This project provides a generic
interface for locally caching log records on disk prior to sending them 
over the wire, and recovering and sending unsent records on restart in the
event of a crash.  You simply pass it the remote `LogSink` instance and the
location on disk to cache log records.
 * `bunyan-v2-mongodb-sink` - A log sink which logs into MongoDB
