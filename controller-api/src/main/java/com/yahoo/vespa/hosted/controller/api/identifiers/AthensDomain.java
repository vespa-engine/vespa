// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

/**
 * @author smorgrav
 */
public class AthensDomain extends Identifier {

    public AthensDomain(String id) {
        super(id);
    }

    public boolean isTopLevelDomain() {
        return !id().contains(".");
    }

    public AthensDomain getParent() {
        return new AthensDomain(id().substring(0, lastDot()));
    }

    public String getName() {
        return id().substring(lastDot() + 1);
    }

    private int lastDot() {
        return id().lastIndexOf('.');
    }

}
