// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.api.identifiers.Identifier;

/**
 * An exception which indicates that a requested resource does not exist.
 *
 * @author Tony Vaagenes
 */
public class NotExistsException extends IllegalArgumentException {

    public NotExistsException(String message) {
        super(message);
    }

    /**
     * Example message: Tenant 'myId' does not exist.
     *
     * @param capitalizedType e.g. Tenant, Application
     * @param id The id of the entity that didn't exist.
     *
     */
    public NotExistsException(String capitalizedType, String id) {
        super(Text.format("%s '%s' does not exist", capitalizedType, id));
    }

    public NotExistsException(Identifier id) {
        this(id.capitalizedType(), id.id());
    }
    
}
