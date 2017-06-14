// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import java.util.Arrays;
import java.util.List;

/**
 * Splits and component id or component specification string
 * into their constituent parts.
 * @author tonytv
 */
class SpecSplitter {
    final String name;
    final String version;
    final ComponentId namespace;

    SpecSplitter(String spec) {
        List<String> idAndNamespace = splitFirst(spec, '@');
        List<String> nameAndVersion = splitFirst(idAndNamespace.get(0), ':');

        name = nameAndVersion.get(0);
        version = second(nameAndVersion);
        namespace = ComponentId.fromString(second(idAndNamespace));
    }

    private String second(List<String> components) {
        return components.size() == 2?
                components.get(1) :
                null;
    }

    private static List<String> splitFirst(String string, char c) {
        int index = string.indexOf(c);
        if (index != -1) {
            if (index == string.length() - 1) {
                throw new RuntimeException("Expected characters after '" + c + "'");
            }
            return Arrays.asList(string.substring(0, index),
                    string.substring(index + 1));
        } else {
            return Arrays.asList(string, null);
        }
    }
}
