// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;

/**
 * @author Einar M R Rosenvinge
 */
public class RankOperation implements FieldOperation {

    private Boolean literal = null;
    private Boolean filter = null;
    private Boolean normal = null;

    public Boolean getLiteral() { return literal; }
    public void setLiteral(Boolean literal) { this.literal = literal; }

    public Boolean getFilter() { return filter; }
    public void setFilter(Boolean filter) { this.filter = filter; }

    public Boolean getNormal() { return normal; }
    public void setNormal(Boolean n) { this.normal = n; }

    public void apply(SDField field) {
        if (literal != null) {
            field.getRanking().setLiteral(literal);
        }
        if (filter != null) {
            field.getRanking().setFilter(filter);
        }
        if (normal != null) {
            field.getRanking().setNormal(normal);
        }
    }

}
