// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;


/**
 * Wrapper for document summary parameters and configuration.
 *
 * @author Steinar Knutsen
 */
public class SummaryParameters {

    public final String defaultClass;

    public SummaryParameters(String defaultClass) {
        if (defaultClass != null && defaultClass.isEmpty())
            this.defaultClass = null;
        else
            this.defaultClass = defaultClass;
    }

}
