// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;

/**
 * @author Tony Vaagenes
 */
public class ComponentGroup <CGCHILD extends Component<?, ?>> extends ConfigProducerGroup<CGCHILD> {

    public ComponentGroup(TreeConfigProducer<AnyConfigProducer> parent, String subId) {
        super(parent, subId);
    }

    public void addComponent(CGCHILD producer) {
        super.addComponent(producer.getComponentId(), producer);
    }

}
