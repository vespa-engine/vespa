// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.vespa.hosted.controller.api.identifiers.Identifier;

/**
 * @author Tony Vaagenes
 */
public class AlreadyExistsException extends IllegalArgumentException {

    /**
     * Example message: Tenant 'myId' already exists.
     *
     * @param capitalizedType e.g. Tenant, Application
     * @param id The id of the entity that didn't exist.
     *
     */
    public AlreadyExistsException(String capitalizedType, String id) {
        super(String.format("%s '%s' already exists", capitalizedType, id));
    }

    public AlreadyExistsException(Identifier identifier) {
        this(identifier.capitalizedType(), identifier.id());
    }

}
