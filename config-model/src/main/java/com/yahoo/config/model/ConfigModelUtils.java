// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * Utilities for config models
 *
 * @author gjoranv
 */
// TODO: Split this into appropriate classes, or move to ConfigModel superclass
public class ConfigModelUtils implements Serializable {

    private static final long serialVersionUID = 1L;

    public static Pattern hourNmin = Pattern.compile("(\\d\\d):(\\d\\d)");

    public static Map<String, Integer> day2int;
    static {
        day2int = new HashMap<>();
        day2int.put("sunday", 0);
        day2int.put("monday", 1);
        day2int.put("tuesday", 2);
        day2int.put("wednesday", 3);
        day2int.put("thursday", 4);
        day2int.put("friday", 5);
        day2int.put("saturday", 6);
    }

    /** Parses a 24 hour clock that must be the five characters ##:## to an int stating minutes after midnight. */
    public static int getTimeOfDay(String time) {
        Matcher m = ConfigModelUtils.hourNmin.matcher(time);
        if (m.matches()) {
            return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
        }
        throw new IllegalArgumentException("The string '" + time + "' is not in ##:## format.");
    }

    /** Parses a day of week name in english to an int, where 0 is sunday, 6 saturday. */
    public static int getDayOfWeek(String day) {
        return ConfigModelUtils.day2int.get(toLowerCase(day));
    }

}
