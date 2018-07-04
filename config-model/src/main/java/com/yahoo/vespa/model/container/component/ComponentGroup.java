// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * @author Tony Vaagenes
 */
public class ComponentGroup <CHILD extends Component<?, ?>> extends ConfigProducerGroup<CHILD> {

    public ComponentGroup(AbstractConfigProducer parent, String subId) {
        super(parent, subId);
    }

    public void addComponent(CHILD producer) {
        super.addComponent(producer.getComponentId(), producer);
    }

}
