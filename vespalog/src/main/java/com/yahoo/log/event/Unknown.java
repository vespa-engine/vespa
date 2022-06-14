// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

/**
 *
 * @author Bjorn Borud
 */
class Unknown extends Event {
    public Unknown() {
    }

    private String name;

    public Unknown setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String getName() {
        return this.name.toLowerCase();
    }
}
