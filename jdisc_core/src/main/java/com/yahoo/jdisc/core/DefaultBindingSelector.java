// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.application.BindingSetSelector;

import java.net.URI;

/**
 * @author Simon Thoresen Hult
 */
public class DefaultBindingSelector implements BindingSetSelector {

    @Override
    public String select(URI uri) {
        return BindingSet.DEFAULT;
    }

}
