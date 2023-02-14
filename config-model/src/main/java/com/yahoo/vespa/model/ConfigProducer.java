// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigInstance.Builder;
import com.yahoo.config.model.producer.UserConfigRepo;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/**
 * Interface that should be implemented by all config producing modules
 * in the vespa model.
 *
 * @author gjoranv
 */
public interface ConfigProducer extends com.yahoo.config.ConfigInstance.Producer {

    /** Returns the configId of this ConfigProducer. */
    String getConfigId();

    /** Returns the one and only HostSystem of the root node */
    HostSystem hostSystem();

    /** Returns the user configs of this */
    UserConfigRepo getUserConfigs();
    
    /** Returns this ConfigProducer's children (only 1st level) */
    default Map<String,? extends ConfigProducer> getChildren() { return Map.of(); }

    /** Returns a List of all Services that are descendants to this ConfigProducer */
    List<Service> getDescendantServices();

    /**
     * Dump the tree of config producers to the specified stream.
     * 
     * @param out The stream to print to, e.g. System.out
     */
    default void dump(PrintStream out) {
        for (ConfigProducer c : getChildren().values()) {
            out.println("id: " + c.getConfigId());
            if (c.getChildren().size() > 0) {
                c.dump(out);
            }
        }
    }

    /**
     * Build config from this and all parent ConfigProducers,
     * such that the root node's config will be added first, and this
     * ConfigProducer's config last in the returned builder.
     *
     * @param builder The builder implemented by the concrete ConfigInstance class
     * @return true if a model config producer was found, so config was applied
     */
    boolean cascadeConfig(Builder builder);

    /**
     * Adds user config override from this ConfigProducer to the existing builder
     *
     * @param builder The ConfigBuilder to add user config overrides.
     * @return true if overrides were added, false if not.
     */
    boolean addUserConfig(ConfigInstance.Builder builder);

    /**
     * check constraints depending on the state of the vespamodel graph.
     * When overriding, you must invoke super.
     */
    void validate() throws Exception;

}
