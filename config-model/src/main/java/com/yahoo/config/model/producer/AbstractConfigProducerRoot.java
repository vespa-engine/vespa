// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.producer;

import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.vespa.model.ConfigProducer;
import com.yahoo.vespa.model.ConfigProducerRoot;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The parent class of classes having the role as the root of a config producer tree.
 *
 * @author Tony Vaagenes
 */
public abstract class AbstractConfigProducerRoot extends TreeConfigProducer<AnyConfigProducer>
        implements ConfigProducerRoot {

    /** The ConfigProducers contained in this model indexed by config id */
    protected final Map<String, ConfigProducer> id2producer = new LinkedHashMap<>();

    public AbstractConfigProducerRoot(String rootConfigId) {
        super(rootConfigId);
    }

    public AbstractConfigProducerRoot getRoot() {
        return this;
    }

    public abstract FileDistributionConfigProducer getFileDistributionConfigProducer();

    /**
     * Freezes the parent - child connections of the model
     * and sets information derived from the topology.
     */
    public void freezeModelTopology() {
        freeze();
        setupConfigId("");
        aggregateDescendantServices();
    }

    public abstract ConfigModelRepo configModelRepo();

    /**
     * Returns the ConfigProducer with the given id if such configId exists.
     *
     * @param  configId The configId, e.g. "search.0/tld.0"
     * @return ConfigProducer with the given configId
     */
    public Optional<ConfigProducer> getConfigProducer(String configId) {
        return Optional.ofNullable(id2producer.get(configId));
    }

    /**
     * Returns the Service with the given id if such configId exists and it belongs to a Service ConfigProducer.
     *
     * @param  configId The configId, e.g. "search.0/tld.0"
     * @return Service with the given configId
     */
    public Optional<Service> getService(String configId) {
        return getConfigProducer(configId)
                .filter(Service.class::isInstance)
                .map(Service.class::cast);
    }
}
