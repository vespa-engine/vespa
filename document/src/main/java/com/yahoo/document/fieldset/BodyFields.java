// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldset;

import com.yahoo.document.Field;

/**
 * Created with IntelliJ IDEA.
 * User: thomasg
 * Date: 4/25/12
 * Time: 3:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class BodyFields implements FieldSet {
    @Override
    public boolean contains(FieldSet o) {
        if (o instanceof BodyFields || o instanceof DocIdOnly || o instanceof NoFields) {
            return true;
        }

        if (o instanceof Field) {
            return !((Field) o).isHeader();
        }

        if (o instanceof FieldCollection) {
            FieldCollection c = (FieldCollection)o;
            for (Field f : c) {
                if (f.isHeader()) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public FieldSet clone() throws CloneNotSupportedException {
        return new BodyFields();
    }
}
