// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

import com.yahoo.log.LogLevel;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Event class is the base class for all VESPA defined events.
 * All specific Event classes extend this abstract class.  An event
 * is more or less a type and a set of properties.  For convenience
 * we use the logging system to transport Event instances, so the
 * typical use is to serialize events into the payload of log
 * messages.
 *
 * <P>
 * Note that the static methods started(), stopped() etc are for use
 * with this class so using them in the subclasses isn't really
 * sanctioned.  These methods are what the user wants to use for
 * logging events, rather than making events him/herself and stuffing
 * them through the logging API.
 *
 * @author  Bjorn Borud
 */
public abstract class Event implements Serializable {
    private static Logger log = Logger.getLogger(Event.class.getName());

    // states for the event parameters
    private static final int INITIAL     = 0;
    private static final int IN_NAME     = 1;
    private static final int IN_UNQUOTED = 2;
    private static final int IN_QUOTED   = 3;
    private static final int EQUALS      = 4;

    private static Pattern whitespace = Pattern.compile("\\s");
    private static Pattern eventFmt = Pattern.compile("^([^/]+)/(\\d+)(.*)$");

    // stash the package name for the instantiation
    private static String packageName = Event.class.getPackage().getName();

    private Map<String,String> values = new LinkedHashMap<String,String>(5);

    // default version number is always 1
    private int version = 1;

    private long time = -1;

    protected Event () {}

    /**
     * Set a property.
     *
     * @param name The name of the property
     * @param value The value of the property
     */
    public Event setValue (String name, String value) {
        values.put(name, value);
        return this;
    }

    /**
     * Get a property value.
     */
    public String getValue (String name) {
        return values.get(name);
    }

    /**
     * Set the timestamp of this event.
     */
    public void setTime (long time) {
        this.time = time;
    }

    /**
     * Get the timestamp of this event
     *
     * @return returns the timestamp of this event
     */
    public long getTime () {
        return time;
    }

    /**
     * Set event version
     *
     * @param version The version of the event.
     */
    public Event setVersion (int version) {
        this.version = version;
        return this;
    }

    /**
     * Get the event version.
     *
     * @return event version
     */
    public int getVersion () {
        return version;
    }

    /**
     * Convenience method which returns a property.  If the
     * property contains whitespace the property will be
     * enclosed in quotes.
     *
     * FIXME: quotes inside the value are not quoted
     *
     */
    public String getValuePossiblyQuote (String name) {
        String tmp =  values.get(name);
        if (tmp == null) {
            return "";
        }

        Matcher m = whitespace.matcher(tmp);
        if (m.find()) {
            return new StringBuffer(tmp.length() + 2)
                .append("\"")
                .append(tmp)
                .append("\"")
                .toString();
        }

        return tmp;
    }

    /**
     * Get the name of the event instance.
     *
     * @return the name of the event instance.
     */
    public String getName() {
        String tmp = this.getClass().getName();
        int last = tmp.lastIndexOf(".");
        if (last == -1) {
            return tmp.toLowerCase();
        }

        return tmp.substring(last+1).toLowerCase();
    }

    /**
     * This method returns the string representation of the
     * event and must return something that can be parsed
     * by the parse method.
     */
    public String toString () {
        StringBuilder buff = new StringBuilder(128)
            .append(getName())
            .append("/")
            .append(version);

        for (String name : values.keySet()) {
            buff.append(" ")
            .append(name)
            .append("=")
            .append(getValuePossiblyQuote(name));
        }

        return buff.toString();
    }


    /**
     * Okay, so I am good at making state machine based parsers,
     * but every time I do it it feels uncomfortable because it
     * isn't the sort of thing one ought to do by freehand :-).
     *
     * <P>
     * Enjoy the graphic
     *
     * <PRE>
     *    ___
     *    ',_`""\        .---,
     *       \   :~""``/`    |
     *        `;'     //`\   /
     *        /   __     |   ('.
     *       |_ ./O)\     \  `) \
     *      _/-.    `      `"`  |`-.
     *  .-=: `                  /   `-.
     * /o o \   ,_,           .        '.
     * L._._;_.-'           .            `'-.
     *   `'-.`             '                 `'-.
     *       `.         '                        `-._
     *         '-._. -'                              '.
     *            \                                    `\
     *
     *
     *
     * </PRE>
     */
    private static void parseValuePairs (String s, Event event) {
        int state = INITIAL;
        int i     = 0;
        int mark  = 0;
        String name = null;

        while (i < s.length()) {
            switch(s.charAt(i)) {

                case ' ':
                    if (state == IN_UNQUOTED) {
                        state = INITIAL;
                        event.setValue(name, s.substring(mark, i));
                    }

                    if (state == INITIAL) {
                        mark = -1;
                        break;
                    }

                    if (state == IN_QUOTED) {
                        break;
                    }

                    throw new IllegalStateException("space not allowed at " + i);

                case '=':
                    if (state == IN_NAME) {
                        name = s.substring(mark, i);
                        state = EQUALS;
                        break;
                    }
                    if (state == IN_QUOTED) {
                        break;
                    }

                    throw new IllegalStateException("'=' not allowed at " + i);

                case '"':
                    if (state == EQUALS) {
                        state = IN_QUOTED;
                        mark = i;
                        break;
                    }

                    if (state == IN_QUOTED) {
                        // skip escaped
                        if (s.charAt(i-1) == '\\') {
                            break;
                        }
                        event.setValue(name, s.substring(mark+1, i));
                        state = INITIAL;
                        break;
                    }

                    throw new IllegalStateException("'\"' not allowed at " + i);

                // ordinary characters
                default:
                    if (state == INITIAL) {
                        state = IN_NAME;
                        mark = i;
                        break;
                    }

                    if (state == EQUALS) {
                        state = IN_UNQUOTED;
                        mark = i;
                        break;
                    }
            }
            i++;
        }

        // mopping up.  when there is no more input to be processed
        // we need to take action if we are in one of the below states
        switch (state) {
            case IN_UNQUOTED:
                event.setValue(name, s.substring(mark, i));
                break;

            case IN_QUOTED:
                event.setValue(name, s.substring(mark+1, i));
                break;

            case IN_NAME:
                throw new IllegalStateException("ended in name");

            case EQUALS:
                event.setValue(name, null);
                break;
        }
    }


    /**
     * Parse string representation of Event and emit correct Event
     * subtype.
     *
     * @param s A string containing an event
     * @return Event represented by <code>s</code>.
     * @throws MalformedEventException if unable to deciper Event
     *           from string.
     */
    public static Event parse (String s) throws MalformedEventException {
        Matcher m1 = eventFmt.matcher(s);
        if (! m1.matches()) {
            throw new MalformedEventException(s);
        }
        String eventName = m1.group(1);
        String eventVersion = m1.group(2);
        String rest = m1.group(3);

        String className = new StringBuffer(eventName.length()
                                            + packageName.length()
                                            + 1)
            .append(packageName)
            .append(".")
            .append(eventName.substring(0,1).toUpperCase())
            .append(eventName.substring(1).toLowerCase())
            .toString();

        Event event;
        try {
            event = (Event) Class.forName(className).getDeclaredConstructor().newInstance();
        }
        catch (ClassNotFoundException e) {
            event = new Unknown().setName(eventName);
        }
        catch (Exception e) {
            log.log(Level.WARNING, "Event instantiation problem", e);
            return null;
        }

        event.setVersion(Integer.parseInt(eventVersion));

        try {
            parseValuePairs(rest, event);
        }
        catch (IllegalStateException | NumberFormatException e) {
            throw new MalformedEventException(e);
        }

        return event;
    }

    /**
     * Find the stack frame of the last called before we entered
     * the Event class and get the Logger belonging to that class.
     * If for some reason we fail to do this, just return the
     * default Logger.
     *
     * <P>
     * Beware, if you come here for cleverness and enlightenment
     * then know that this technique might not be exceptionally fast
     * so don't abuse this mechanism blindly.  (Do what I do:  abuse
     * it with both eyes open :-).
     *
     */
    private static final Logger getCallerLogger() {
        StackTraceElement stack[] = (new Throwable()).getStackTrace();
        int i = 0;
        while (i < stack.length) {
            StackTraceElement frame = stack[i];
            String cname = frame.getClassName();
            if (cname.equals("com.yahoo.log.event.Event")) {
                break;
            }
            i++;
        }

        while (i < stack.length) {
            StackTraceElement frame = stack[i];
            String cname = frame.getClassName();
            if (!cname.equals("com.yahoo.log.event.Event")) {
                return Logger.getLogger(cname);
            }
            i++;
        }

        return Logger.getLogger("");
    }

    /**
     * Internal method which prepares Event log messages.  Not
     * the prettiest way to do it...
     */
    private static final void log(Logger logger, Object param) {
        LogRecord r = new LogRecord(LogLevel.EVENT, null);
        r.setParameters(new Object[] {param});
        r.setLoggerName(logger.getName());
        logger.log(r);
    }

    /**
     * Static method for logging the <b>starting</b> event.
     */
    public static final void starting (String name) {
        log(getCallerLogger(), new Starting(name));
    }

    /**
     * Static method for logging the <b>started</b> event.
     */
    public static final void started (String name) {
        log(getCallerLogger(), new Started(name));
    }

    /**
     * Static method for logging the <b>stopping</b> event.
     */
    public static final void stopping (String name, String why) {
        log(getCallerLogger(), new Stopping(name, why));
    }

    /**
     * Static method for logging the <b>stopped</b> event.
     */
    public static final void stopped (String name, int pid, int exitcode) {
        log(getCallerLogger(), new Stopped(name, pid, exitcode));
    }

    /**
     * Static method for logging the <b>reloading</b> event.
     */
    public static final void reloading (String name) {
        log(getCallerLogger(), new Reloading(name));
    }

    /**
     * Static method for logging the <b>reloaded</b> event.
     */
    public static final void reloaded (String name) {
        log(getCallerLogger(), new Reloaded(name));
    }

    /**
     * Static method for logging the <b>count</b> event.
     */
    public static final void count (String name, long value) {
        log(getCallerLogger(), new Count(name, value));
    }

    /**
     * Static method for logging the <b>value</b> event.
     */
    public static final void value (String name, double value) {
        log(getCallerLogger(), new Value(name, value));
    }

    /**
     * Static method for logging the <b>histogram</b> event.
     */
    public static final void histogram (String name, String value,
                                        String representation) {
        log(getCallerLogger(), new Histogram(name, value,
                                             representation));
    }

    /**
     * Static method for logging a set of <b>value</b> events.
     */
    public static final void valueGroup (String name, String value) {
        log(getCallerLogger(), new ValueGroup(name, value));
    }

    /**
     * Static method for logging a set of <b>count</b> events.
     */
    public static final void countGroup (String name, String value) {
        log(getCallerLogger(), new CountGroup(name, value));
    }

    /**
     * Static method for logging the <b>progress</b> event.
     */
    public static final void progress (String name, long value, long total) {
        log(getCallerLogger(), new Progress(name, value, total));
    }

    /**
     * Static method for logging the <b>state</b> event.
     */
    public static final void state (String name, String value) {
        log(getCallerLogger(), new State(name, value));
    }

    /**
     * Static method for logging the <b>crash</b> event.
     */
    public static final void crash (String name, int pid, int signal) {
        log(getCallerLogger(), new Crash(name, pid, signal));
    }
}
