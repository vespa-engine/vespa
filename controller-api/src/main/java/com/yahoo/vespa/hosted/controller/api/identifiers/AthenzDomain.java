// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

/**
 * @author bjorncs
 * @author smorgrav
 */
public class AthenzDomain extends Identifier {

    public AthenzDomain(String id) {
        super(id);
    }

    public boolean isTopLevelDomain() {
        return !id().contains(".");
    }

    public AthenzDomain getParent() {
        return new AthenzDomain(id().substring(0, lastDot()));
    }

    public String getNameSuffix() {
        return id().substring(lastDot() + 1);
    }

    private int lastDot() {
        return id().lastIndexOf('.');
    }

}
