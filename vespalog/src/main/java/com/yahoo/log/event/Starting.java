// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

/**
 *
 * @author  Bjorn Borud
 */
class Starting extends Event {
    public Starting () {
    }

    public Starting (String name) {
        setValue("name", name);
    }
}
