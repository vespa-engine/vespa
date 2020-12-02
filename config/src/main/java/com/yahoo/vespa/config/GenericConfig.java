// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.ConfigBuilder;
import com.yahoo.config.ConfigInstance;

/**
 *
 * A generic config with an internal generic builder that mimics a real config builder in order to support builders
 * when we don't have the schema.
 *
 * @author Ulf Lilleengen
 */
public class GenericConfig {

    public static class GenericConfigBuilder implements ConfigInstance.Builder {

        private final ConfigPayloadBuilder payloadBuilder;
        private final ConfigDefinitionKey defKey;

        public GenericConfigBuilder(ConfigDefinitionKey defKey, ConfigPayloadBuilder payloadBuilder) {
            this.defKey = defKey;
            this.payloadBuilder = payloadBuilder;
        }

        @SuppressWarnings("unused") // Called by reflection
        private ConfigBuilder override(GenericConfigBuilder superior) {
            payloadBuilder.override(superior.payloadBuilder);
            return this;
        }

        public ConfigPayload getPayload() { return ConfigPayload.fromBuilder(payloadBuilder); }

        @Override
        public boolean dispatchGetConfig(ConfigInstance.Producer producer) {
            return false;
        }

        @Override
        public String getDefName() {
            return defKey.getName();
        }

        @Override
        public String getDefNamespace() {
            return defKey.getNamespace();
        }

        @Override
        public String getDefMd5() {
            return "";
        }

    }

}
