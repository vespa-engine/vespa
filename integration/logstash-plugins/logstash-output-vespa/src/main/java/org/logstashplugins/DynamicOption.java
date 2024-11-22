// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package org.logstashplugins;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * If the config value matches %{field_name} or %{[field_name]}, it's dynamic
 */
public class DynamicOption {

    String dynamicRegex = "%\\{\\[?(.*?)]?}";
    String parsedConfigValue;
    boolean isDynamic;

    public DynamicOption(String configValue) {
        Pattern dynamicPattern = Pattern.compile(dynamicRegex);
        Matcher matcher = dynamicPattern.matcher(configValue);

        if (matcher.matches()) {
            isDynamic = true;
            parsedConfigValue = matcher.group(1);
        } else {
            isDynamic = false;
            parsedConfigValue = configValue;
        }
    }

    public String getParsedConfigValue() {
        return parsedConfigValue;
    }

    public boolean isDynamic() {
        return isDynamic;
    }

}
