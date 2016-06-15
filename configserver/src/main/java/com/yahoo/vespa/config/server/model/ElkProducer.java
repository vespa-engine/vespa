// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.model;
import com.yahoo.cloud.config.ElkConfig.Builder;

import com.yahoo.cloud.config.ElkConfig;
import com.yahoo.vespa.defaults.Defaults;

/**
 * Produces the ELK config for the SuperModel
 * 
 * @author vegardh
 * @since 5.38
 *
 */
public class ElkProducer implements ElkConfig.Producer {

    private final ElkConfig config;

    public ElkProducer(ElkConfig config) {
        this.config = config;
    }

    @Override
    public void getConfig(Builder builder) {
        for (ElkConfig.Elasticsearch es : config.elasticsearch()) {
            int port = es.port() != 0 ? es.port() : Defaults.getDefaults().vespaWebServicePort();
            builder.elasticsearch(new ElkConfig.Elasticsearch.Builder().host(es.host()).port(port));
        }
        ElkConfig.Logstash.Builder logstashBuilder = new ElkConfig.Logstash.Builder();        
        logstashBuilder.
                        config_file(Defaults.getDefaults().underVespaHome(config.logstash().config_file())).
                        source_field(config.logstash().source_field()).
                        spool_size(config.logstash().spool_size());
        ElkConfig.Logstash.Network.Builder networkBuilder = new ElkConfig.Logstash.Network.Builder().
                        timeout(config.logstash().network().timeout());
        for (ElkConfig.Logstash.Network.Servers srv : config.logstash().network().servers()) {
            networkBuilder.
                        servers(new ElkConfig.Logstash.Network.Servers.Builder().
                                        host(srv.host()).
                                        port(srv.port()));
        }
        logstashBuilder.network(networkBuilder);
        for (ElkConfig.Logstash.Files files : config.logstash().files()) {
            logstashBuilder.files(new ElkConfig.Logstash.Files.Builder().
                            paths(files.paths()).
                            fields(files.fields()));            
        }
        builder.logstash(logstashBuilder);
    }
    
}
