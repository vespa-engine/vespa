// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component.chain;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.container.component.Component;


/**
 * @author Tony Vaagenes
 * @author gjoranv
 *
 * Base class for all ChainedComponent config producers.
 */
public class ChainedComponent<T extends ChainedComponentModel> extends Component<AnyConfigProducer, T> {

    public ChainedComponent(T model) {
        super(model);
    }

    public void initialize() {}

    @Override
    public ComponentId getGlobalComponentId() {
        return model.getComponentId().nestInNamespace(namespace());
    }

    private ComponentId namespace() {
        var owner = getParent().getParent();
        return (owner instanceof Chain) ?
                ((Chain) owner).getGlobalComponentId() :
                null;
    }
}
