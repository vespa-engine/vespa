// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.logging.Level;


/**
 * Note that the log levels defined in VESPA applications are the
 * following.
 *
 * <UL>
 *  <LI> LogLevel.EVENT   (1201)
 *  <LI> LogLevel.FATAL   (1151)
 *  <LI> LogLevel.ERROR   (1101)
 *  <LI> <em>LogLevel.SEVERE  (1000)</em>
 *  <LI> LogLevel.WARNING  (900)
 *  <LI> LogLevel.INFO     (800)
 *  <LI> LogLevel.CONFIG   (700)
 *  <LI> LogLevel.DEBUG    (501)
 *  <LI> LogLevel.SPAM     (299)
 * </UL>
 *
 * <P>
 * Note that the EVENT level is somewhat special and you must
 * <b>never</b> log one of these messages manually, but use
 * the {@link com.yahoo.log.event.Event} class for this.
 *
 * @author  Bjorn Borud
 * @author arnej27959
 */

public class LogLevel extends Level {
    /** A map from the name of the log level to the instance */
    private static LinkedHashMap<String, Level> nameToLevel;

    /** A map from the java.util.logging loglevel to VESPA's loglevel */
    private static Map<Level, Level> javaToVespa;

    public static final int IntValEVENT   = 1201;
    public static final int IntValFATAL   = 1161;
    public static final int IntValERROR   = 1101;
    public static final int IntValUNKNOWN = 1001;
    public static final int IntValSEVERE  = 1000;
    public static final int IntValWARNING = 900;
    public static final int IntValINFO    = 800;
    public static final int IntValCONFIG  = 700;
    public static final int IntValDEBUG   = 501;
    public static final int IntValFINE    = 500;
    public static final int IntValFINER   = 400;
    public static final int IntValFINEST  = 300;
    public static final int IntValSPAM    = 299;

    // these define the ordering of the Vespa levels logcontrol files.
    // it must match the values of the LogLevel enum in <log/log.h>
    // for the C++ framework:
    // fatal, error, warning, config, info, event, debug, spam, NUM_LOGLEVELS

    public static final int LogCtlFATAL   = 0;
    public static final int LogCtlERROR   = 1;
    public static final int LogCtlWARNING = 2;
    public static final int LogCtlCONFIG  = 3;
    public static final int LogCtlINFO    = 4;
    public static final int LogCtlEVENT   = 5;
    public static final int LogCtlDEBUG   = 6;
    public static final int LogCtlSPAM    = 7;
    public static final int LogCtlNumLevels = 8;

    // ordinary log levels
    public static LogLevel UNKNOWN = new LogLevel("UNKNOWN", IntValUNKNOWN);
    public static LogLevel EVENT   = new LogLevel("EVENT",   IntValEVENT);
    public static LogLevel FATAL   = new LogLevel("FATAL",   IntValFATAL);
    public static LogLevel ERROR   = new LogLevel("ERROR",   IntValERROR);
    public static LogLevel DEBUG   = new LogLevel("DEBUG",   IntValDEBUG);
    public static LogLevel SPAM    = new LogLevel("SPAM",    IntValSPAM);

    // overlapping ones, only mentioned for illustration
    //
    // public static LogLevel WARNING = new LogLevel("WARNING",900);
    // public static LogLevel INFO    = new LogLevel("INFO",800);
    // public static LogLevel CONFIG  = new LogLevel("CONFIG",700);

    static {
        // define mapping from Java log levels to VESPA log
        // levels.
        javaToVespa = new HashMap<Level, Level>();
        javaToVespa.put(Level.SEVERE, ERROR);
        javaToVespa.put(Level.WARNING, WARNING);
        javaToVespa.put(Level.INFO, INFO);
        javaToVespa.put(Level.CONFIG, CONFIG);
        javaToVespa.put(Level.FINE, DEBUG);
        javaToVespa.put(Level.FINER, DEBUG);
        javaToVespa.put(Level.FINEST, SPAM);

        // need the VESPA ones too
        javaToVespa.put(FATAL, FATAL);
        javaToVespa.put(ERROR, ERROR);
        javaToVespa.put(EVENT, EVENT);
        javaToVespa.put(DEBUG, DEBUG);
        javaToVespa.put(SPAM, SPAM);

        // manually enter the valid log levels we shall recognize
        // in VESPA
        nameToLevel = new LinkedHashMap<String, Level>(15);
        nameToLevel.put("fatal", FATAL);
        nameToLevel.put("error", ERROR);
        nameToLevel.put("warning", WARNING);
        nameToLevel.put("config", CONFIG);
        nameToLevel.put("info", INFO);
        nameToLevel.put("event", EVENT);
        nameToLevel.put("debug", DEBUG);
        nameToLevel.put("spam", SPAM);
    }

    private LogLevel(String name, int value) {
        super(name, value);
    }

    /**
     * Semi-Case sensitive parsing of log levels.  <b>Log levels are
     * in either all upper case or all lower case.  Not mixed
     * case. </b>. Returns static instance representing log level or
     * the UNKNOWN LogLevel instance.
     *
     * @param name Name of loglevel in uppercase or lowercase.
     * @return Returns the static (immutable) LogLevel instance
     *         equivalent to the name given.
     *
     */
    public static Level parse(String name) {
        Level l = nameToLevel.get(name);
        if (l == null) {
            return UNKNOWN;
        }
        return l;
    }

    /**
     * Static method for mapping Java log level to VESPA log level.
     *
     * @param level The Java loglevel we want mapped to its VESPA
     *              counterpart
     * @return The VESPA LogLevel instance representing the corresponding
     *         log level (or nearest normal level numerically if not in map)
     */
    public static Level getVespaLogLevel(Level level) {
        Level ll = javaToVespa.get(level);
        if (ll != null) {
            return ll;
        }
        int lv = level.intValue();
        if (lv > WARNING.intValue()) {
            return ERROR;
        }
        if (lv > INFO.intValue()) {
            return WARNING;
        }
        if (lv > DEBUG.intValue()) {
            return INFO;
        }
        if (lv > FINEST.intValue()) {
            return DEBUG;
        }
        return SPAM;
    }

    /**
     * Static method returning a map from Vespa level name to Level
     *
     * @return a map from Vespa level name to Level
     */
    public static HashMap<String, Level> getLevels() {
        return nameToLevel;
    }
}
