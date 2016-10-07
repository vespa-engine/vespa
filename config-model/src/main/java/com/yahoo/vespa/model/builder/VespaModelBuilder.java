// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder;

import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.vespa.model.generic.service.ServiceCluster;

import java.util.List;

/**
 * Base class for classes capable of building vespa model.
 *
 * @author vegardh
 */
public abstract class VespaModelBuilder {


    public abstract ApplicationConfigProducerRoot getRoot(String name, DeployState deployState, AbstractConfigProducer parent);
    public abstract List<ServiceCluster> getClusters(ApplicationPackage pkg, AbstractConfigProducer parent);

    /**
     * Processing that requires access across plugins
     * @param producerRoot The root producer.
     * @param configModelRepo a {@link com.yahoo.config.model.ConfigModelRepo instance}
     */
    public abstract void postProc(AbstractConfigProducer producerRoot, ConfigModelRepo configModelRepo);

}
