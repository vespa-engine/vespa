// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

/**
 * The context in which some text is linguistically processes
 *
 * @author bratseth
 */
public class LinguisticsContext {

    private final String documentTypeName;

    public LinguisticsContext(String documentTypeName) {
        this.documentTypeName = documentTypeName;
    }

    public String documentTypeName() { return documentTypeName; }

}
