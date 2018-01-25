// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Definition of a document type used for testing.
 *
 * @author geirst
 */
public class DocType {
    private final String type;
    private final String mode;
    private final boolean global;

    private DocType(String type, String mode, boolean global) {
        this.type = type;
        this.mode = mode;
        this.global = global;
    }

    public String toXml() {
        return (global ? "<document mode='" + mode + "' type='" + type + "' global='true'/>" :
                "<document mode='" + mode + "' type='" + type + "'/>");
    }

    public static DocType storeOnly(String type) {
        return new DocType(type, "store-only", false);
    }

    public static DocType index(String type) {
        return new DocType(type, "index", false);
    }

    public static DocType indexGlobal(String type) {
        return new DocType(type, "index", true);
    }

    public static DocType streaming(String type) {
        return new DocType(type, "streaming", false);
    }

    public static String listToXml(DocType... docTypes) {
        return listToXml(Arrays.asList(docTypes));
    }

    public static String listToXml(List<DocType> docTypes) {
        return "<documents>\n" +
                docTypes.stream().map(DocType::toXml).collect(Collectors.joining("\n")) + "\n" +
                "</documents>";
    }

}
