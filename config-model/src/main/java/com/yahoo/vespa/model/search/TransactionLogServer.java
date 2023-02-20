// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.searchlib.TranslogserverConfig;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import org.w3c.dom.Element;

/**
 * @author hmusum
 */
public class TransactionLogServer extends AbstractService  {

    private final Boolean useFsync;

    public TransactionLogServer(TreeConfigProducer<?> searchNode, String clusterName, Boolean useFsync) {
        super(searchNode, "transactionlogserver");
        portsMeta.on(0).tag("tls");
        this.useFsync = useFsync;
        setProp("clustername", clusterName);
        setProp("clustertype", "search");
    }

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilderBase<TransactionLogServer> {

        private final String clusterName;
        private final Boolean useFsync;
        public Builder(String clusterName, Boolean useFsync) {
            this.clusterName = clusterName;
            this.useFsync = useFsync;
        }

        @Override
        protected TransactionLogServer doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element producerSpec) {
            return new TransactionLogServer(ancestor, clusterName, useFsync);
        }

    }

    public int getPortCount() {
        return 1;
    }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        // NB: ignore "start"
        from.allocatePort("tls");
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
        builder.listenport(getTlsPort())
                .basedir(getTlsDir());
        if (useFsync != null) {
            builder.usefsync(useFsync);
        }
    }

}
