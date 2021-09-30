// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Stemming;

/**
 * @author Einar M R Rosenvinge
 */
public class StemmingOperation implements FieldOperation {

    private String setting;

    public String getSetting() {
        return setting;
    }

    public void setSetting(String setting) {
        this.setting = setting;
    }

    public void apply(SDField field) {
        field.setStemming(Stemming.get(setting));
    }

}
