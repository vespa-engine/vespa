// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.docproc;

import com.yahoo.collections.Pair;
import com.yahoo.config.docproc.DocprocConfig;
import com.yahoo.config.docproc.SchemamappingConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.container.jdisc.ContainerMbusConfig;
import com.yahoo.container.jdisc.config.SessionConfig;
import com.yahoo.docproc.jdisc.messagebus.MbusRequestContext;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerThreadpool;
import com.yahoo.vespa.model.container.component.ContainerSubsystem;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Einar M R Rosenvinge
 * @author gjoranv
 */
public class ContainerDocproc extends ContainerSubsystem<DocprocChains> implements
        ContainerMbusConfig.Producer,
        SchemamappingConfig.Producer,
        DocprocConfig.Producer {

    public final Options options;
    private final Map<Pair<String, String>, String> fieldNameSchemaMap = new HashMap<>();

    public ContainerDocproc(ContainerCluster<?> cluster, DocprocChains chains) {
        this(cluster, chains, Options.empty());
    }

    private ContainerDocproc(ContainerCluster<?> cluster, DocprocChains chains, Options options) {
        this(cluster, chains, options, true);
    }

    private void addSource(ContainerCluster<?> cluster, String name, SessionConfig.Type.Enum type) {
        final MbusClient mbusClient = new MbusClient(name, type);
        mbusClient.addClientBindings(SystemBindingPattern.fromPattern("mbus://*/" + mbusClient.getSessionName()));
        cluster.addComponent(mbusClient);
    }

    public ContainerDocproc(ContainerCluster<?> cluster, DocprocChains chains, Options options, boolean addSourceClientProvider) {
        super(chains);
        assert (options != null) : "Null Options for " + this + " under cluster " + cluster.getName();
        this.options = options;

        if (addSourceClientProvider) {
            addSource(cluster, "source", SessionConfig.Type.SOURCE);
            addSource(cluster, MbusRequestContext.internalNoThrottledSource, SessionConfig.Type.INTERNAL);
        }
        cluster.addSearchAndDocprocBundles();
    }

    @Override
    public void getConfig(ContainerMbusConfig.Builder builder) {
        builder.maxpendingcount(getMaxMessagesInQueue());
    }

    private int getMaxMessagesInQueue() {
        if (options.maxMessagesInQueue != null) {
            return options.maxMessagesInQueue;
        }

        // maxmessagesinqueue has not been set for this node. let's try to give a good value anyway:
        return 2048 * getChains().allChains().allComponents().size();
        // intentionally high, getMaxQueueMbSize() will probably kick in before this one!
    }

    private Integer getMaxQueueTimeMs() {
        return options.maxQueueTimeMs;
    }

    @Override
    public void getConfig(DocprocConfig.Builder builder) {
        if (getMaxQueueTimeMs() != null) {
            builder.maxqueuetimems(getMaxQueueTimeMs());
        }
    }
    
    @Override
    public void getConfig(SchemamappingConfig.Builder builder) {
        Map<Pair<String, String>, String> allMappings = new HashMap<>();
        for (DocprocChain chain : getChains().allChains().allComponents()) {
            for (DocumentProcessor processor : chain.getInnerComponents()) {
                allMappings.putAll(fieldNameSchemaMap());
                allMappings.putAll(chain.fieldNameSchemaMap());
                allMappings.putAll(processor.fieldNameSchemaMap());
                for (Map.Entry<Pair<String,String>, String> e : allMappings.entrySet()) {
                    String doctype = e.getKey().getFirst();
                    String from = e.getKey().getSecond();
                    String to = e.getValue();
                    builder.fieldmapping(new SchemamappingConfig.Fieldmapping.Builder().
                            chain(chain.getId().stringValue()).
                            docproc(processor.getGlobalComponentId().stringValue()).
                            indocument(from).
                            inprocessor(to).
                            doctype(doctype!=null?doctype:""));
                }
                allMappings.clear();
            }
        }
    }
    
    /**
     * The field name schema map that applies to this whole chain
     * @return doctype,from → to
     */
    public Map<Pair<String,String>,String> fieldNameSchemaMap() {
        return fieldNameSchemaMap;
    }

    public static class Options {

        public final Element threadpoolXml;
        public final Integer maxMessagesInQueue;
        public final Integer maxQueueTimeMs;

        public final Double maxConcurrentFactor;
        public final Double documentExpansionFactor;
        public final Integer containerCoreMemory;

        public Options(Integer maxMessagesInQueue, Integer maxQueueTimeMs, Double maxConcurrentFactor, Double documentExpansionFactor, Integer containerCoreMemory, Element threadpoolXml) {
            this.maxMessagesInQueue = maxMessagesInQueue;
            this.maxQueueTimeMs = maxQueueTimeMs;
            this.maxConcurrentFactor = maxConcurrentFactor;
            this.documentExpansionFactor = documentExpansionFactor;
            this.containerCoreMemory = containerCoreMemory;
            this.threadpoolXml = threadpoolXml;
        }

        static Options empty() { return new Options(null, null, null, null, null, null); }

    }

    public static class Threadpool extends ContainerThreadpool {

        private final double threadsScale;

        public Threadpool(DeployState ds, Element options) {
            super(ds, "docproc-handler", options);
            threadsScale = ds.featureFlags().documentProcessorHandlerThreadpoolThreads();
        }

        @Override
        public void setDefaultConfigValues(ContainerThreadpoolConfig.Builder builder) {
            double wantedNumber;
            if (threadsScale > 0) {
                var processors = Runtime.getRuntime().availableProcessors();
                wantedNumber = Math.max(processors * threadsScale, 1);
            } else {
                wantedNumber = Math.max(-threadsScale, 1);
            }

            builder.maxThreadExecutionTimeSeconds(190)
                    .keepAliveTime(5.0)
                    .maxThreads((int)wantedNumber)
                    .minThreads((int)wantedNumber)
                    .queueSize(-40);
        }

    }

}
