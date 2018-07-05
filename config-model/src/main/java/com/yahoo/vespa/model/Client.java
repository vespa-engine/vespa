// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * This is a placeholder config producer that makes global configuration available through a single identifier. This
 * is added directly to the {@link ApplicationConfigProducerRoot} producer, and so can be accessed by the simple "client" identifier.
 *
 * @author Simon Thoresen Hult
 */
public class Client extends AbstractConfigProducer {

    /**
     * Constructs a client config producer that is added as a child to
     * the given config producer.
     *
     * @param parent The parent config producer.
     */
    public Client(AbstractConfigProducer parent) {
        super(parent, "client");
    }

}
