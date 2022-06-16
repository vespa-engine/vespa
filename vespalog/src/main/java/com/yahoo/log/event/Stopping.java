// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

/**
 *
 * @author  Bjorn Borud
 */
class Stopping extends Event {
    public Stopping () {
    }

    public Stopping (String name, String why) {
        setValue("name", name);
        setValue("why", why);
    }
}
