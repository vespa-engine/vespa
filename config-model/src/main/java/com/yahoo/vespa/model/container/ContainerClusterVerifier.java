package com.yahoo.vespa.model.container;

import com.yahoo.vespa.model.container.component.Component;

/**
 *
 * Operations on a container cluster are verified against this verifier
 * This can be used for ignoring, consitency checking.
 *
 *  * @author baldersheim
 *
 */
public interface ContainerClusterVerifier {
    /**
     * Return true if you accept the component.
     * @param component name of component that wants to be added
     * @return true if you accept it
     */
    boolean acceptComponent(Component component);

    /**
     * Return true if you accept the container.
     * @param container container to add
     * @return true if you accept it
     */
    boolean acceptContainer(Container container);
}
