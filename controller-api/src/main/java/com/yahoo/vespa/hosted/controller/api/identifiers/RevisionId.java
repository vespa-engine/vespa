// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

/**
 * An unique identifier of an application package.
 * 
 * @author smorgrav
 */
public class RevisionId extends Identifier {

    public RevisionId(String id) {
        super(id);
    }

}
