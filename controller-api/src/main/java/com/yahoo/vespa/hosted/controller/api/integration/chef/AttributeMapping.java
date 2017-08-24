// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.chef;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author mortent
 */
public class AttributeMapping {

    private final String attribute;
    private final List<String> chefPath;

    private AttributeMapping(String attribute, List<String> chefPath) {
        this.chefPath = chefPath;
        this.attribute = attribute;
    }

    public static AttributeMapping simpleMapping(String attribute) {
        return new AttributeMapping(attribute, Collections.singletonList(attribute));
    }

    public static AttributeMapping deepMapping(String attribute, List<String> chefPath) {
        return new AttributeMapping(attribute, chefPath);
    }

    public String toString() {
        return String.format("\"%s\": [%s]", attribute,
                chefPath.stream().map(s -> String.format("\"%s\"", s))
                .collect(Collectors.joining(","))
        );
    }
}
