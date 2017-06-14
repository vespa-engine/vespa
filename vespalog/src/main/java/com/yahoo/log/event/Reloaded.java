// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

/**
 *
 * @author  <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 */
public class Reloaded extends Event {
    public Reloaded () {
    }

    public Reloaded (String name) {
        setValue("name", name);
    }
}
