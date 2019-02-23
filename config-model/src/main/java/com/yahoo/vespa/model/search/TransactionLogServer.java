// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.searchlib.TranslogserverConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import org.w3c.dom.Element;

/**
 * @author hmusum
 */
public class TransactionLogServer extends AbstractService  {

    private static final long serialVersionUID = 1L;

    public TransactionLogServer(AbstractConfigProducer searchNode, String clusterName) {
        super(searchNode, "transactionlogserver");
        portsMeta.on(0).tag("tls");
        setProp("clustername", clusterName);
        setProp("clustertype", "search");
    }

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilder<TransactionLogServer> {
        private final String clusterName;
        public Builder(String clusterName) {
            this.clusterName = clusterName;
        }

        @Override
        protected TransactionLogServer doBuild(DeployState deployState, AbstractConfigProducer ancestor, Element producerSpec) {
            return new TransactionLogServer(ancestor, clusterName);
        }
    }

    public int getPortCount() {
        return 1;
    }

    @Override
    public String[] getPortSuffixes() {
        return new String[]{"tls"};
    }

    /**
     * Returns the port used by the TLS.
     *
     * @return The port.
     */
    int getTlsPort() {
        return getRelativePort(0);
    }

    /**
     * Returns the directory used by the TLS.
     *
     * @return The directory.
     */
    private String getTlsDir() {
        return "tls";
    }

    public void getConfig(TranslogserverConfig.Builder builder) {
        builder.listenport(getTlsPort()).basedir(getTlsDir());
    }

}
