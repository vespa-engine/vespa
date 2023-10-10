// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

/**
 * Sets up a simple component to keep the state of the cluster controller, even when configuration changes.
 */
public class ClusterControllerComponent extends SimpleComponent
{
    public ClusterControllerComponent() {
        super(new ComponentModel(new BundleInstantiationSpecification(
                new ComponentSpecification("clustercontroller"),
                new ComponentSpecification("com.yahoo.vespa.clustercontroller.apps.clustercontroller.ClusterController"),
                new ComponentSpecification("clustercontroller-apps"))));
    }
}
