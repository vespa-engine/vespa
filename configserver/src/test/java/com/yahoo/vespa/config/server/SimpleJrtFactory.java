// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.container.jdisc.jrt.JrtFactory;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;

/**
 * @author bjorncs
 */
public class SimpleJrtFactory implements JrtFactory {

    @Override
    public Supervisor createSupervisor() {
        return new Supervisor(new Transport());
    }

}
