// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;

/**
 * @author Einar M R Rosenvinge
 */
public class SummaryInFieldShortOperation extends SummaryInFieldOperation {

    public SummaryInFieldShortOperation(String name) {
        super(name);
    }

    public void apply(SDField field) {
        SummaryField ret = field.getSummaryField(name);
        if (ret == null) {
            ret = new SummaryField(name, field.getDataType());
            ret.addSource(field.getName());
            ret.addDestination("default");
        }
        ret.setImplicit(false);

        ret.setTransform(transform);
        for (SummaryField.Source source : sources) {
            ret.addSource(source);
        }
        field.addSummaryField(ret);
    }

}
