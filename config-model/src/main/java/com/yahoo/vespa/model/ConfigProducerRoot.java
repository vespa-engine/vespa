// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.admin.Admin;

import java.util.Set;


/**
 * Intended to be used as an external interface to the vespa model root.
 *
 * @author Tony Vaagenes
 */
public interface ConfigProducerRoot extends ConfigProducer {

    /**
     * Adds the given producer (at any depth level) as descendant to this root nodes.
     *
     * @param id string id of descendant
     * @param descendant the producer to add to this root node
     */
    void addDescendant(String id, AnyConfigProducer descendant);

    /**
     * @return an unmodifiable copy of the set of configIds in this root.
     */
    Set<String> getConfigIds();

    ConfigInstance.Builder getConfig(ConfigInstance.Builder builder, String configId);

    /**
     * Resolves config of the given type and config id.
     * @param clazz The type of config
     * @param configId The config id
     * @return A config instance of the given type
     */
    <CONFIGTYPE extends ConfigInstance> CONFIGTYPE getConfig(Class<CONFIGTYPE> clazz, String configId);

    Admin getAdmin();

}
