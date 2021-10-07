// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

/**
 *
 * @author  Bjorn Borud
 */
public class Collection extends Event {
    public Collection () {
    }

    public Collection (long collectionId, String name) {
        setValue("collectionId", Long.toString(collectionId));
        setValue("name", name);
    }
}
