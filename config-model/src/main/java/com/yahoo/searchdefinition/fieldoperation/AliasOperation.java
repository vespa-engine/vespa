// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;

/**
 * @author Einar M R Rosenvinge
 */
public class AliasOperation implements FieldOperation {

    private String aliasedName;
    private String alias;

    public AliasOperation(String aliasedName, String alias) {
        this.aliasedName = aliasedName;
        this.alias = alias;
    }

    public String getAliasedName() {
        return aliasedName;
    }

    public void setAliasedName(String aliasedName) {
        this.aliasedName = aliasedName;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public void apply(SDField field) {
        if (aliasedName == null) {
            aliasedName = field.getName();
        }
        field.getAliasToName().put(alias, aliasedName);
    }

}
