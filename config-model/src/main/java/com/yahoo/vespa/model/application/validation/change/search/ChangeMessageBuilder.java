// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder of a message describing the changes in a given field.
 *
 * @author geirst
 */
public class ChangeMessageBuilder {

    private final String fieldName;
    private final List<String> changes = new ArrayList<>();

    public ChangeMessageBuilder(String fieldName) {
        this.fieldName = fieldName;
    }

    public String build() {
        return "Field '" + fieldName + "' changed: " + String.join(", ", changes);
    }

    public ChangeMessageBuilder addChange(String component, String from, String to) {
        changes.add(component + ": '" + from + "' -> '" + to + "'");
        return this;
    }

    public ChangeMessageBuilder addChange(String message) {
        changes.add(message);
        return this;
    }

}
