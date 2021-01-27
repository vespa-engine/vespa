// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.cloud.config.ApplicationIdConfig;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolPoliciesConfig;
import com.yahoo.vespa.config.content.DistributionConfig;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.document.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.messagebus.protocol.DocumentrouteselectorpolicyConfig;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;


/**
 * This interface describes the configs that are produced by the model producer root.
 *
 * @author Ulf Lilleengen
 * @since 5.1
 */
public interface CommonConfigsProducer extends DocumentmanagerConfig.Producer,
                                               DocumenttypesConfig.Producer,
                                               MessagebusConfig.Producer,
                                               DocumentrouteselectorpolicyConfig.Producer,
                                               DocumentProtocolPoliciesConfig.Producer,
                                               LogdConfig.Producer,
                                               SlobroksConfig.Producer,
                                               ZookeepersConfig.Producer,
                                               LoadTypeConfig.Producer,
                                               ClusterListConfig.Producer,
                                               DistributionConfig.Producer,
                                               AllClustersBucketSpacesConfig.Producer,
                                               ModelConfig.Producer,
                                               ApplicationIdConfig.Producer {
}
