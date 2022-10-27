// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool;

import org.apache.commons.cli.CommandLine;

/**
 * @author vekterli
 */
public class CliUtils {

    public static String optionOrThrow(CommandLine arguments, String option) {
        var value = arguments.getOptionValue(option);
        if (value == null) {
            throw new IllegalArgumentException("Required argument '--%s' must be provided".formatted(option));
        }
        return value;
    }

}
