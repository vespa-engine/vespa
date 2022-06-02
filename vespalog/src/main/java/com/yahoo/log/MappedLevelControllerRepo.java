// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import com.yahoo.text.Utf8;

import java.nio.MappedByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains a repository of mapped log level controllers.
 *
 * @author Ulf Lilleengen
 * @since 5.1
 * Should only be used internally in the log library
 */
class MappedLevelControllerRepo {
    private final Map<String, LevelController> levelControllerMap = new HashMap<>();
    private final MappedByteBuffer mapBuf;
    private final int controlFileHeaderLength;
    private final int numLevels;
    private final String logControlFilename;

    MappedLevelControllerRepo(MappedByteBuffer mapBuf, int controlFileHeaderLength, int numLevels, String logControlFilename) {
        this.mapBuf = mapBuf;
        this.controlFileHeaderLength = controlFileHeaderLength;
        this.numLevels = numLevels;
        this.logControlFilename = logControlFilename;
        buildMap();
    }

    private void buildMap() {
        int len = mapBuf.capacity();
        int startOfLine = controlFileHeaderLength;

        int numLine = 1;
        int i = 0;
        while (i < len) {
            if (mapBuf.get(i) == '\n') {
                startOfLine = ++i;
                ++numLine;
            } else if (i < controlFileHeaderLength) {
                ++i;
            } else if (mapBuf.get(i) == ':') {
                int endOfName = i;
                int levels = i;
                levels += 2;
                while ((levels % 4) != 0) {
                    levels++;
                }
                int endLine = levels + 4*numLevels;

                if (checkLine(startOfLine, endOfName, levels, endLine)) {
                    int l = endOfName - startOfLine;
                    if (l > 1 && mapBuf.get(startOfLine) == '.') {
                        ++startOfLine;
                        --l;
                    }
                    byte[] namebytes = new byte[l];
                    for (int j = 0; j < l; j++) {
                        namebytes[j] = mapBuf.get(startOfLine + j);
                    }
                    String name = Utf8.toString(namebytes);
                    if (name.equals("default")) {
                        name = "";
                    }
                    MappedLevelController ctrl = new MappedLevelController(mapBuf, levels, name);
                    levelControllerMap.put(name, ctrl);
                    i = endLine;
                    continue; // good line
                }
                // bad line, skip
                while (i < len && mapBuf.get(i) != '\n') {
                    i++;
                }
                int bll = i - startOfLine;
                byte[] badline = new byte[bll];
                for (int j = 0; j < bll; j++) {
                    badline[j] = mapBuf.get(startOfLine + j);
                }
                System.err.println("bad loglevel line "+numLine+" in "
                                   + logControlFilename + ": " + Utf8.toString(badline));
            } else {
                i++;
            }
        }
    }

    private boolean checkLine(int sol, int endnam, int levstart, int eol) {
        if (eol >= mapBuf.capacity()) {
            System.err.println("line would end after end of file");
            return false;
        }
        if (mapBuf.get(eol) != '\n') {
            System.err.println("line must end with newline, was: "+mapBuf.get(eol));
            return false;
        }
        if (endnam < sol + 1) {
            System.err.println("name must be at least one character after start of line");
            return false;
        }
        return MappedLevelController.checkOnOff(mapBuf, levstart);
    }

    LevelController getLevelController(String suffix) {

        return levelControllerMap.get(suffix);
    }

    void checkBack() {
        for (LevelController ctrl : levelControllerMap.values()) {
            ctrl.checkBack();
        }
    }
}
