// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.generic.service;

import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * A simple sub service that is essentially just to have a node with a nice name
 * in the tree. Could might as well have used an AbstractConfigProducer as well,
 * but that makes the code very confusing to read.
 *
 * @author Ulf Lilleengen
 */
public class Module extends AbstractConfigProducer<Module> {

    public Module(AbstractConfigProducer<?> parent, String subId) {
        super(parent, subId);
    }
}
