// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;

import java.util.List;

/**
 * @author Einar M R Rosenvinge
 */
public class QueryCommandOperation implements FieldOperation {

    private final List<String> queryCommands = new java.util.ArrayList<>(0);

    public void addQueryCommand(String name) {
       queryCommands.add(name);
    }

    public void apply(SDField field) {
        for (String command : queryCommands) {
            field.addQueryCommand(command);
        }
    }

}
