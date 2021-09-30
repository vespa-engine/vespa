// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Sorting;

/**
 * @author Einar M R Rosenvinge
 */
public class SortingOperation implements FieldOperation {

    private final String attributeName;
    private Boolean ascending;
    private Boolean descending;
    private Sorting.Function function;
    private Sorting.Strength strength;
    private String locale;

    public SortingOperation(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public Boolean getAscending() {
        return ascending;
    }

    public void setAscending() {
        this.ascending = true;
    }

    public Boolean getDescending() {
        return descending;
    }

    public void setDescending() {
        this.descending = true;
    }

    public Sorting.Function getFunction() {
        return function;
    }

    public void setFunction(Sorting.Function function) {
        this.function = function;
    }

    public Sorting.Strength getStrength() {
        return strength;
    }

    public void setStrength(Sorting.Strength strength) {
        this.strength = strength;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public void apply(SDField field) {
        Attribute attribute = field.getAttributes().get(attributeName);
        if (attribute == null) {
            attribute = new Attribute(attributeName, field.getDataType());
            field.addAttribute(attribute);
        }
        Sorting sorting = attribute.getSorting();

        if (ascending != null) {
            sorting.setAscending();
        }
        if (descending != null) {
            sorting.setDescending();
        }
        if (function != null) {
            sorting.setFunction(function);
        }
        if (strength != null) {
            sorting.setStrength(strength);
        }
        if (locale != null) {
            sorting.setLocale(locale);
        }
    }

}
