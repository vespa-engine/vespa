// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.client.dsl;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class UserInput extends QueryChain {

    Annotation annotation; // accept only defaultIndex annotation
    String value;
    String indexField;
    String placeholder; // for generating unique param
    boolean setDefaultIndex;

    UserInput(Sources sources, String value) {
        this(sources, A.empty(), value);
    }

    UserInput(Sources sources, Annotation annotation, String value) {
        this.sources = sources;
        this.annotation = annotation;
        this.value = value;
        this.nonEmpty = true;

        if (annotation.annotations.containsKey("defaultIndex")) {
            setDefaultIndex = true;
            indexField = (String) annotation.annotations.get("defaultIndex");
        } else {
            indexField = UUID.randomUUID().toString().substring(0, 5);
        }
    }

    UserInput(String value) {
        this(A.empty(), value);
    }

    UserInput(Annotation annotation, String value) {
        this(null, annotation, value);
    }

    public void setIndex(int index) {
        placeholder = setDefaultIndex
                      ? "_" + index + "_" + indexField
                      : "_" + index;
    }

    @Override
    public String toString() {
        //([{"defaultIndex": "shpdescfree"}](userInput(@_shpdescfree_1)))
        return setDefaultIndex
               ? String.format("([%s]userInput(@%s))", annotation, placeholder)
               : String.format("userInput(@%s)", placeholder);
    }


    Map<String, String> getParam() {
        return Collections.singletonMap(placeholder, value);
    }

    @Override
    boolean hasPositiveSearchField(String fieldName) {
        return !"andnot".equals(this.op) && this.indexField.equals(fieldName);
    }

    @Override
    boolean hasPositiveSearchField(String fieldName, Object value) {
        return hasPositiveSearchField(fieldName) && this.value.equals(value);
    }

    @Override
    boolean hasNegativeSearchField(String fieldName) {
        return "andnot".equals(this.op) && this.indexField.equals(fieldName);
    }

    @Override
    boolean hasNegativeSearchField(String fieldName, Object value) {
        return hasNegativeSearchField(fieldName) && this.value.equals(value);
    }
}
