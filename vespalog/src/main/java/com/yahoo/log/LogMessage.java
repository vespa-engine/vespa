// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import com.yahoo.log.event.Event;
import com.yahoo.log.event.MalformedEventException;

import java.util.OptionalLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class implements the common ground log message used by
 * the logserver.  A LogMessage is immutable.  Note that we have
 * chosen the name LogMessage to avoid confusion with LogRecord
 * which is used in java.util.logging.
 *
 * @author  Bjorn Borud
 */
public class LogMessage
{
    private static Logger log = Logger.getLogger(LogMessage.class.getName());

    private static Pattern nativeFormat =
        Pattern.compile("^(\\d[^\t]+)\t" + // time
                        "([^\t]+)\t"  + // host
                        "([^\t]+)\t"  + // threadProcess
                        "([^\t]+)\t"  + // service
                        "([^\t]+)\t"  + // component
                        "([^\t]+)\t"  + // level
                        "(.+)$"         // payload
                        );

    private long     time;
    private String   timeStr;
    private String   host;
    private long     processId;
    private long     threadId;
    private String   service;
    private String   component;
    private Level    level;
    private String   payload;
    private Event    event;

    /**
     * Private constructor.  Log messages should never be instantiated
     * directly; only as the result of a static factory method.
     */
    private LogMessage (String timeStr, Long time, String host, long processId, long threadId,
                        String service, String component, Level level,
                        String payload)
    {
        this.timeStr = timeStr;
        this.time = time;
        this.host = host;
        this.processId = processId;
        this.threadId = threadId;
        this.service = service;
        this.component = component;
        this.level = level;
        this.payload = payload;
    }

    public long     getTime ()          {return time;}
    public long     getTimeInSeconds () {return time / 1000;}
    public String   getHost ()          {return host;}
    public long     getProcessId()      {return processId;}
    public OptionalLong getThreadId()      {return threadId != -1 ? OptionalLong.of(threadId) : OptionalLong.empty();}
    /**
     * @deprecated Use {@link #getProcessId()} / {@link #getThreadId()}
     */
    @Deprecated(since = "7", forRemoval = true)
    public String   getThreadProcess () {return VespaFormat.formatThreadProcess(processId, threadId);}
    public String   getService ()       {return service;}
    public String   getComponent ()     {return component;}
    public Level    getLevel ()         {return level;}
    public String   getPayload ()       {return payload;}

    /**
     * Make a log message from the native format of the logging
     * package.
     *
     * @param msg The log message
     * @return Returns a LogMessage instance
     * @throws InvalidLogFormatException if the log message
     *    can not be parsed, ie. is invalid, we throw this
     *    exception.
     */
    public static LogMessage parseNativeFormat(String msg) throws InvalidLogFormatException {
        Matcher m = nativeFormat.matcher(msg);
        if (! m.matches()) {
            throw new InvalidLogFormatException(msg);
        }

        Level msgLevel = LogLevel.parse(m.group(6));
        Long timestamp = parseTimestamp(m.group(1));
        String threadProcess = m.group(3);

        return new LogMessage(m.group(1), timestamp, m.group(2), parseProcessId(threadProcess), parseThreadId(threadProcess),
                              m.group(4), m.group(5), msgLevel,
                              m.group(7));
    }

    private static long parseTimestamp(String timeStr) throws InvalidLogFormatException {
        try {
            return (long) (Double.parseDouble(timeStr) * 1000);
        } catch (NumberFormatException e) {
            throw new InvalidLogFormatException("Invalid time string:" + timeStr);
        }
    }

    private static long parseProcessId(String threadProcess) {
        int slashIndex = threadProcess.indexOf('/');
        if (slashIndex == -1) {
            return Long.parseLong(threadProcess);
        }
        return Long.parseLong(threadProcess.substring(0, slashIndex));
    }

    private static long parseThreadId(String threadProcess) {
        int slashIndex = threadProcess.indexOf('/');
        if (slashIndex == -1) {
            return -1;
        }
        return Long.parseLong(threadProcess.substring(slashIndex + 1));
    }

    /**
     * If the LogMessage was an EVENT then this method can
     * be used to get the Event instance representing the
     * event.  The event instance created the first time
     * this method is called and then cached.
     *
     * TODO: make sure this throws exception!
     *
     * @return Returns Event instance if this is an event message
     *         and the payload is correctly formatted.  Otherwise
     *         it will return <code>null</code>.
     *
     */
    public Event getEvent () throws MalformedEventException {
        if ((level == LogLevel.EVENT) && (event == null)) {
            try {
                event = Event.parse(getPayload());
                event.setTime(time);
            }
            catch (MalformedEventException e) {
                log.log(LogLevel.DEBUG, "Got malformed event: " + getPayload());
                throw e;
            }
        }
        return event;
    }

    /**
     * Return valid representation of log message.
     */
    public String toString () {
        String threadProcess = VespaFormat.formatThreadProcess(processId, threadId);
        return new StringBuilder(timeStr.length()
                                + host.length()
                                + threadProcess.length()
                                + service.length()
                                + component.length()
                                + level.toString().length()
                                + payload.length()
                                + 1)
            .append(timeStr).append("\t")
            .append(host).append("\t")
            .append(threadProcess).append("\t")
            .append(service).append("\t")
            .append(component).append("\t")
            .append(level.toString().toLowerCase()).append("\t")
            .append(payload).append("\n")
            .toString();
    }
}
