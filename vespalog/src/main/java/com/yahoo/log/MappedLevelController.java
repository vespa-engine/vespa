// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import com.yahoo.text.Utf8;

import java.nio.MappedByteBuffer;
import java.util.logging.Level;

/**
 * a level controller that does lookup in a file via a memory-mapped
 * buffer for realtime logging control.
 * Should only be used internally in the log library
 */
@SuppressWarnings("deprecation")
class MappedLevelController implements LevelController {
    private static final int ONVAL  = 0x20204f4e; // equals "  ON" in file
    private static final int OFFVAL = 0x204f4646; // equals " OFF" in file

    private MappedByteBuffer mapBuf;
    private int offset;
    private java.util.logging.Logger associate;
    public MappedLevelController(MappedByteBuffer buf,
                                 int firstoffset,
                                 String name)
    {
        this.mapBuf = buf;
        this.offset = firstoffset;
        this.associate = java.util.logging.Logger.getLogger(name);
    }

    /**
     * return the current state as a string
     * (directly fetched from the file via the mapping buffer)
     **/
    public String getOnOffString() {
        byte[] levels = new byte[4 * VespaLevelControllerRepo.numLevels];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = mapBuf.get(offset + i);
        }
        return Utf8.toString(levels);
    }

    /**
     * check that each controlled level is either ON or OFF.
     **/
    public static boolean checkOnOff(MappedByteBuffer mapBuf,
                                     int offset)
    {
        for (int i = 0; i < VespaLevelControllerRepo.numLevels; i++) {
            int off = offset + 4 * i;
            int val = mapBuf.getInt(off);
            if (val != ONVAL && val != OFFVAL) {
                System.err.println("bad on/off value: "+val);
                return false;
            }
        }
        return true;
    }

    /**
     * make sure our associated java.util.Logger instance
     * gets the correct logging level so it can avoid sending
     * us lots of debug and spam log messages that will
     * be discarded in the usual case.
     **/
    public void checkBack() {
        associate.setLevel(getLevelLimit());
    }
    public Level getLevelLimit() {
        Level lvl;
        if (isOn(LogLevel.LogCtlSPAM)) {
            lvl = LogLevel.ALL;
        } else if (isOn(LogLevel.LogCtlDEBUG)) {
            lvl = LogLevel.FINE;
        } else if (isOn(LogLevel.LogCtlCONFIG)) {
            lvl = LogLevel.CONFIG;
        } else if (isOn(LogLevel.LogCtlINFO)) {
            lvl = LogLevel.INFO;
        } else if (isOn(LogLevel.LogCtlWARNING)) {
            lvl = LogLevel.WARNING;
        } else {
            lvl = LogLevel.SEVERE;
        }
        return lvl;
    }

    /**
     * is a specific Vespa level ON or OFF in the file?
     **/
    private boolean isOn(int num) {
        int off = offset + num*4;
        int val = mapBuf.getInt(off);
        if (val == OFFVAL)
            return false;
        return true;
    }

    /**
     * should we publish a log messages on the given java Level?
     **/
    public boolean shouldLog(Level level) {
        int val = level.intValue();

        // event is special and handled first:
        if (val == LogLevel.IntValEVENT)   { return isOn(LogLevel.LogCtlEVENT); }

        // all other levels are handled in "severity order":

        if (val >= LogLevel.IntValFATAL)   { return isOn(LogLevel.LogCtlFATAL); }
        // LogLevel.ERROR between here
        if (val >= LogLevel.IntValSEVERE)  { return isOn(LogLevel.LogCtlERROR); }
        if (val >= LogLevel.IntValWARNING) { return isOn(LogLevel.LogCtlWARNING); }
        if (val >= LogLevel.IntValINFO)    { return isOn(LogLevel.LogCtlINFO); }
        if (val >= LogLevel.IntValCONFIG)  { return isOn(LogLevel.LogCtlCONFIG); }
        // LogLevel.DEBUG between here
        // LogLevel.FINE between here
        if (val >= LogLevel.IntValFINER)   { return isOn(LogLevel.LogCtlDEBUG); }
        // LogLevel.FINEST and
        // LogLevel.SPAM:
        return isOn(LogLevel.LogCtlSPAM);
    }
}
