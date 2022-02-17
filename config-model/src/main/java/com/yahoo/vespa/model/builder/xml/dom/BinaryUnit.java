// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * @author Tony Vaagenes
 */
public class BinaryUnit {

    // The pattern must match the one given in the schema
    private static final Pattern pattern = Pattern.compile("(\\d+(\\.\\d*)?)\\s*([kmgKMG])?");

    public static double valueOf(String valueString) {
        Matcher matcher = pattern.matcher(valueString);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Value '" + valueString + "' does not match the pattern for binary unit");
        }
        double value = Double.valueOf(matcher.group(1));
        String unit = matcher.group(3);
        if (unit != null) {
            value *= unitToValue(toLowerCase(unit).charAt(0));
        }
        return value;
    }

    private static double unitToValue(char unit) {
           char[] units = {'k', 'm', 'g'};
           for (int i = 0; i < units.length; ++i) {
               if (units[i] == unit) {
                   return Math.pow(2, 10*(i+1));
               }
           }

           throw new IllegalArgumentException("No such unit: '" + unit + "'");
    }
}
