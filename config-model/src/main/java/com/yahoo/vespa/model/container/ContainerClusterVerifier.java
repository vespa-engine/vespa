package com.yahoo.vespa.model.container;

import com.yahoo.vespa.model.container.component.Component;

/**
 * @author baldersheim
 *
 * Operations on a container cluster are verified against this verifier
 * This can be used for ignoring, consitency checking.
 */
public interface ContainerClusterVerifier {
    /**
     *
     * @param component name of component that wants to be added
     * @return true if you accept it
     */
    boolean acceptComponent(Component component);

    /**
     *
     * @param container container to add
     * @return true if you accept it
     */
    boolean acceptContainer(Container container);
}
