// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;

import java.util.List;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class QueryCommandOperation implements FieldOperation {
    private List<String> queryCommands = new java.util.ArrayList<>(0);

    public void addQueryCommand(String name) {
       queryCommands.add(name);
    }

    public void apply(SDField field) {
        for (String command : queryCommands) {
            field.addQueryCommand(command);
        }
    }
}
