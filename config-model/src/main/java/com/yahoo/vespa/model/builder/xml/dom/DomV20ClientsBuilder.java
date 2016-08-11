// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Phase;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.model.ConfigModelUtils;
import com.yahoo.vespa.config.content.spooler.SpoolerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.text.XML;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.SimpleConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder.DomConfigProducerBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.docproc.DomDocprocChainsBuilder;
import com.yahoo.vespa.model.clients.Clients;
import com.yahoo.vespa.model.clients.HttpGatewayOwner;
import com.yahoo.vespa.model.clients.VespaSpoolMaster;
import com.yahoo.vespa.model.clients.VespaSpooler;
import com.yahoo.vespa.model.clients.VespaSpoolerProducer;
import com.yahoo.vespa.model.clients.VespaSpoolerService;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.docproc.DocprocChains;
import com.yahoo.vespa.model.container.search.ContainerHttpGateway;
import com.yahoo.vespa.model.container.search.ContainerSearch;
import com.yahoo.vespa.model.container.search.searchchain.SearchChain;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.container.search.searchchain.Searcher;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import com.yahoo.vespaclient.config.FeederConfig;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

/**
 * Builds the Clients plugin
 *
 * @author vegardh
 */
public class DomV20ClientsBuilder {

    public static final String vespaClientBundleSpecification = "vespaclient-container-plugin";

    // The parent docproc plugin to register data with.
    private final Clients clients;

    DomV20ClientsBuilder(Clients clients, String version) {
        this.clients = clients;
        if (!version.equals("2.0")) {
            throw new IllegalArgumentException("Version '" + version + "' of 'clients' not supported.");
        }
    }

    public void build(Element spec) {
        NodeList children = spec.getElementsByTagName("gateways");
        if (children.getLength() > 0 && clients.getConfigProducer()!=null)
            clients.getConfigProducer().deployLogger().log(Level.WARNING, "The 'gateways' element is deprecated, and will be disallowed in a " +
                    "later version of Vespa. Use 'document-api' under 'jdisc' instead, see: " +
                    ConfigModelUtils.createDocLink("reference/services-jdisc.html"));
        for (int i = 0; i < children.getLength(); i++) {
            createGateways(clients.getConfigProducer(), (Element) children.item(i), clients);
        }

        children = spec.getElementsByTagName("spoolers");
        for (int i = 0; i < children.getLength(); i++) {
            createSpoolers(clients.getConfigProducer(), (Element) children.item(i), clients);
        }

        children = spec.getElementsByTagName("load-types");
        for (int i = 0; i < children.getLength(); i++) {
            createLoadTypes((Element) children.item(i), clients);
        }
    }

    static Boolean getBooleanNodeValue(Node node) {
        return Boolean.valueOf(node.getFirstChild().getNodeValue());
    }

    static boolean getHttpFileServerEnabled(Element parentHttpFileServer, Element httpFileServer) {
        boolean ret=false;
        if (parentHttpFileServer != null) {
            for (Element child : XML.getChildren(parentHttpFileServer)) {
                if ("enabled".equals(child.getNodeName())) {
                    ret = getBooleanNodeValue(child);
                }
            }
        }
        if (httpFileServer != null) {
            for (Element child : XML.getChildren(httpFileServer)) {
                if ("enabled".equals(child.getNodeName())) {
                    ret = getBooleanNodeValue(child);
                }
            }
        }
        return ret;
    }

    private void createLoadTypes(Element element, Clients clients) {
        for (Element e : XML.getChildren(element, "type")) {
            String priority = e.getAttribute("default-priority");
            clients.getLoadTypes().addType(e.getAttribute("name"), priority.length() > 0 ? priority : null);
        }
    }

    /**
     * Creates HttpGateway objects using the given xml Element.
     *
     * @param pcp       AbstractConfigProducer
     * @param element   The xml Element
     */
    private void createGateways(AbstractConfigProducer pcp, Element element, Clients clients) {
        String jvmArgs = null;
        if (element.hasAttribute(VespaDomBuilder.JVMARGS_ATTRIB_NAME)) jvmArgs=element.getAttribute(VespaDomBuilder.JVMARGS_ATTRIB_NAME);

        Element gatewaysFeederOptions = findFeederOptions(element);

        HttpGatewayOwner owner = new HttpGatewayOwner(pcp, getFeederConfig(null, gatewaysFeederOptions));
        ContainerCluster cluster = new ContainerHttpGatewayClusterBuilder().build(owner, element);

        int index = 0;
        for (Element e : XML.getChildren(element, "gateway")) {
            ContainerHttpGateway qrs = new ContainerHttpGatewayBuilder(cluster, index).build(cluster, e);

            if ("".equals(qrs.getJvmArgs()) && jvmArgs!=null) qrs.setJvmArgs(jvmArgs);
            index++;
        }
        clients.setContainerHttpGateways(cluster);
    }

    /**
     * Creates VespaSpooler objects using the given xml Element.
     */
    private void createSpoolers(AbstractConfigProducer pcp, Element element, Clients clients) {
        String jvmArgs = null;
        if (element.hasAttribute(VespaDomBuilder.JVMARGS_ATTRIB_NAME)) jvmArgs=element.getAttribute(VespaDomBuilder.JVMARGS_ATTRIB_NAME);
        SimpleConfigProducer spoolerCfg = new VespaDomBuilder.DomSimpleConfigProducerBuilder(element.getNodeName()).
                build(pcp, element);
        Element spoolersFeederOptions = findFeederOptions(element);
        createSpoolMasters(spoolerCfg, element);
        for (Element e : XML.getChildren(element, "spooler")) {
            String configId = e.getAttribute("id").trim();
            FeederConfig.Builder feederConfig = getFeederConfig(spoolersFeederOptions, e);
            SpoolerConfig.Builder spoolConfig = getSpoolConfig(e);
            if (configId.length() == 0) {
                int index = clients.getVespaSpoolers().size();
                VespaSpoolerService spoolerService = new VespaSpoolerServiceBuilder(index, new VespaSpooler(feederConfig, spoolConfig)).
                        build(spoolerCfg, e);
                if ("".equals(spoolerService.getJvmArgs()) && jvmArgs!=null) spoolerService.setJvmArgs(jvmArgs);
                spoolerService.setProp("index", String.valueOf(index));
                clients.getVespaSpoolers().add(spoolerService);
            } else {
                new VespaSpoolerProducerBuilder(configId, new VespaSpooler(feederConfig, spoolConfig)).
                        build(spoolerCfg, e);
            }
        }
    }

    private void createSpoolMasters(SimpleConfigProducer producer,
            Element element) {
        int i=0;
        for (Element e : XML.getChildren(element, "spoolmaster")) {
            VespaSpoolMaster master = new VespaSpoolMasterBuilder(i).build(producer, e);
            i++;
        }
    }

    private SpoolerConfig.Builder getSpoolConfig(Element conf) {
        SpoolerConfig.Builder builder = new SpoolerConfig.Builder();
        if (conf.getAttributes().getNamedItem("directory") != null) {
            builder.directory(Defaults.getDefaults().underVespaHome(conf.getAttributes().getNamedItem("directory").getNodeValue()));
        }
        if (conf.getAttributes().getNamedItem("keepsuccess") != null) {
            builder.keepsuccess(getBooleanFromAttribute(conf, "keepsuccess"));
        }
        if (conf.getAttributes().getNamedItem("maxfailuresize") != null) {
            builder.maxfailuresize(getIntegerFromAttribute(conf, "maxfailuresize"));
        }
        if (conf.getAttributes().getNamedItem("maxfatalfailuresize") != null) {
            builder.maxfatalfailuresize(getIntegerFromAttribute(conf, "maxfatalfailuresize"));
        }
        if (conf.getAttributes().getNamedItem("threads") != null) {
            builder.threads(getIntegerFromAttribute(conf, "threads"));
        }
        if (conf.getAttributes().getNamedItem("maxretries") != null) {
            builder.maxretries(getIntegerFromAttribute(conf, "maxretries"));
        }

        NodeList children = conf.getElementsByTagName("parsers");
        if (children.getLength() == 1) {
            children = ((Element)children.item(0)).getElementsByTagName("parser");
        }

        for (int i=0; i < children.getLength(); i++) {
            Element e = (Element)children.item(i);

            String type = e.getAttributes().getNamedItem("type").getNodeValue();
            NodeList params = e.getElementsByTagName("parameter");

            SpoolerConfig.Parsers.Builder parserBuilder = new SpoolerConfig.Parsers.Builder();
            parserBuilder.classname(type);
            if (params.getLength() > 0) {
                List<SpoolerConfig.Parsers.Parameters.Builder> parametersBuilders = new ArrayList<>();
                for (int j = 0; j < params.getLength(); j++) {
                    SpoolerConfig.Parsers.Parameters.Builder parametersBuilder = new SpoolerConfig.Parsers.Parameters.Builder();
                    Element p = (Element) params.item(j);
                    parametersBuilder.key(getStringFromAttribute(p, "key"));
                    parametersBuilder.value(getStringFromAttribute(p, "value"));
                    parametersBuilders.add(parametersBuilder);
                }
                parserBuilder.parameters(parametersBuilders);
            }

            builder.parsers.add(parserBuilder);
        }
        return builder;
    }

    Boolean getBooleanFromAttribute(Element e, String attributeName) {
        return Boolean.parseBoolean(e.getAttributes().getNamedItem(attributeName).getNodeValue());
    }

    Integer getIntegerFromAttribute(Element e, String attributeName) {
        return Integer.parseInt(e.getAttributes().getNamedItem(attributeName).getNodeValue());
    }

    String getStringFromAttribute(Element e, String attributeName) {
        return e.getAttributes().getNamedItem(attributeName).getNodeValue();
    }

    private FeederConfig.Builder getFeederConfig(Element gatewaysFeederOptions, Element e) {
        FeederOptionsParser foParser = new FeederOptionsParser();
        if (gatewaysFeederOptions!=null) {
            foParser.parseFeederOptions(gatewaysFeederOptions).getFeederConfig();
        }
        foParser.parseFeederOptions(e);
        return foParser.getFeederConfig();
    }

    /**
     * Finds the feederoptions subelement in the given xml Element.
     *
     * @param element   The xml Element
     * @return          The feederoptions xml Element
     */
    private Element findFeederOptions(Element element) {
        for (Element child : XML.getChildren(element)) {
            if (child.getNodeName().equals("feederoptions")) {
                return child;
            }
        }
        return null;
    }

    private static class VespaSpoolerServiceBuilder extends DomConfigProducerBuilder<VespaSpoolerService> {
        private int index;
        private VespaSpooler spoolerConfig;

        public VespaSpoolerServiceBuilder(int index, VespaSpooler spoolerConfig) {
            this.index = index;
            this.spoolerConfig = spoolerConfig;
        }

        @Override
        protected VespaSpoolerService doBuild(AbstractConfigProducer parent,
                Element spec) {
            return new VespaSpoolerService(parent, index, spoolerConfig);
        }
    }

    private static class VespaSpoolerProducerBuilder extends DomConfigProducerBuilder<VespaSpoolerProducer> {
        private String name=null;
        private VespaSpooler spooler;

        public VespaSpoolerProducerBuilder(String name, VespaSpooler spooler) {
            this.name = name;
            this.spooler = spooler;
        }

        @Override
        protected VespaSpoolerProducer doBuild(AbstractConfigProducer parent,
                Element producerSpec) {
            return new VespaSpoolerProducer(parent, name, spooler);
        }
    }

    private static class VespaSpoolMasterBuilder extends DomConfigProducerBuilder<VespaSpoolMaster> {
        int index;

        public VespaSpoolMasterBuilder(int index) {
            super();
            this.index = index;
        }

        @Override
        protected VespaSpoolMaster doBuild(AbstractConfigProducer parent,
                Element spec) {
            return new VespaSpoolMaster(parent, index);
        }
    }

    public static class ContainerHttpGatewayClusterBuilder extends DomConfigProducerBuilder<ContainerCluster> {
        @Override
        protected ContainerCluster doBuild(AbstractConfigProducer parent,
                                                                 Element spec) {

            ContainerCluster cluster = new ContainerCluster(parent, "gateway", "gateway");

            SearchChains searchChains = new SearchChains(cluster, "searchchain");
            Set<ComponentSpecification> inherited = new TreeSet<>();
            //inherited.add(new ComponentSpecification("vespa", null, null));
            {
            SearchChain mySearchChain = new SearchChain(new ChainSpecification(new ComponentId("vespaget"),
                    new ChainSpecification.Inheritance(inherited, null), new ArrayList<>(), new TreeSet<>()));
            Searcher getComponent = newVespaClientSearcher("com.yahoo.storage.searcher.GetSearcher");
            mySearchChain.addInnerComponent(getComponent);
            searchChains.add(mySearchChain);
            }
            {
                SearchChain mySearchChain = new SearchChain(new ChainSpecification(new ComponentId("vespavisit"),
                        new ChainSpecification.Inheritance(inherited, null), new ArrayList<>(), new TreeSet<>()));
                Searcher getComponent = newVespaClientSearcher("com.yahoo.storage.searcher.VisitSearcher");
                mySearchChain.addInnerComponent(getComponent);
                searchChains.add(mySearchChain);
            }

            ContainerSearch containerSearch = new ContainerSearch(cluster, searchChains, new ContainerSearch.Options());
            cluster.setSearch(containerSearch);

            cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandler", "http://*/feed"));
            cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandlerRemove", "http://*/remove"));
            cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandlerRemoveLocation", "http://*/removelocation"));
            cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandlerGet", "http://*/get"));
            cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandlerVisit", "http://*/visit"));
            cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandlerCompatibility", "http://*/document"));
            cluster.addComponent(newVespaClientHandler("com.yahoo.feedhandler.VespaFeedHandlerStatus", "http://*/feedstatus"));
            final ProcessingHandler<SearchChains> searchHandler = new ProcessingHandler<>(
                    cluster.getSearch().getChains(), "com.yahoo.search.handler.SearchHandler");
            searchHandler.addServerBindings("http://*/search/*");
            cluster.addComponent(searchHandler);

            ContainerModelBuilder.addDefaultHandler_legacyBuilder(cluster);

            //BEGIN HACK for docproc chains:
            DocprocChains docprocChains = getDocprocChains(cluster, spec);
            if (docprocChains != null) {
                ContainerDocproc containerDocproc = new ContainerDocproc(cluster, docprocChains);
                cluster.setDocproc(containerDocproc);
            }
            //END HACK

            return cluster;
        }

        private Handler newVespaClientHandler(String componentId, String binding) {
            Handler<AbstractConfigProducer<?>> handler = new Handler<>(new ComponentModel(
                    BundleInstantiationSpecification.getFromStrings(componentId, null, vespaClientBundleSpecification), ""));
            handler.addServerBindings(binding);
            handler.addServerBindings(binding + '/');
            return handler;
        }

        private Searcher newVespaClientSearcher(String componentSpec) {
            return new Searcher<>(new ChainedComponentModel(
                    BundleInstantiationSpecification.getFromStrings(componentSpec, null, vespaClientBundleSpecification),
                            new Dependencies(null, null, null)));
        }

        //BEGIN HACK for docproc chains:
        private DocprocChains getDocprocChains(AbstractConfigProducer qrs, Element gateways) {
            Element clients = (Element) gateways.getParentNode();
            Element services = (Element) clients.getParentNode();
            if (services == null) {
                return null;
            }

            Element docproc = XML.getChild(services, "docproc");
            if (docproc == null) {
                return null;
            }

            String version = docproc.getAttribute("version");
            if (version.startsWith("1.")) {
                return null;
            } else if (version.startsWith("2.")) {
                return null;
            } else if (version.startsWith("3.")) {
                return getDocprocChainsV3(qrs, docproc);
            } else {
                throw new IllegalArgumentException("Docproc version " + version + " unknown.");
            }
        }

        private DocprocChains getDocprocChainsV3(AbstractConfigProducer qrs, Element docproc) {
            Element docprocChainsElem = XML.getChild(docproc, "docprocchains");
            if (docprocChainsElem == null) {
                return null;
            }
            return new DomDocprocChainsBuilder(null, true).build(qrs, docprocChainsElem);
        }
        //END HACK
    }

    public static class ContainerHttpGatewayBuilder extends DomConfigProducerBuilder<ContainerHttpGateway> {
        int index;
        ContainerCluster cluster;

        public ContainerHttpGatewayBuilder(ContainerCluster cluster, int index) {
            this.index = index;
            this.cluster = cluster;
        }

        @Override
        protected ContainerHttpGateway doBuild(AbstractConfigProducer parent, Element spec) {
            // TODO: remove port handling
            int port = 19020;
            if (spec != null && spec.hasAttribute("baseport")) {
                port = Integer.parseInt(spec.getAttribute("baseport"));
            }
            ContainerHttpGateway httpGateway = new ContainerHttpGateway(cluster, "" + index, port, index);
            List<Container> containers = new ArrayList<>();
            containers.add(httpGateway);

            cluster.addContainers(containers);
            return httpGateway;
        }
    }

    /**
     * This class parses the feederoptions xml tag and produces Vespa config output.
     *
     * @author Gunnar Gauslaa Bergem
     */
    private class FeederOptionsParser implements Serializable {
        private static final long serialVersionUID = 1L;
        // All member variables are objects so that we can switch on null values.
        private Boolean abortondocumenterror = null;
        private String route = null;
        private Integer maxpendingdocs = null;
        private Integer maxpendingbytes = null;
        private Boolean retryenabled = null;
        private Double retrydelay = null;
        private Double timeout = null;
        private Integer tracelevel = null;
        private Integer mbusport = null;
        private String docprocChain = null;

        /**
         * Constructs an empty feeder options object with all members set to null.
         */
        public FeederOptionsParser() {
            // empty
        }

        /**
         * Parses the content of the given XML element as feeder options.
         *
         * @param conf The XML element to parse.
         */
        public FeederOptionsParser parseFeederOptions(Element conf) {
            for (Node node : XML.getChildren(conf)) {
                String nodename = node.getNodeName();
                Node firstchild = node.getFirstChild();
                String childval = (firstchild != null) ? firstchild.getNodeValue() : null;

                switch (nodename) {
                    case "abortondocumenterror":
                        abortondocumenterror = Boolean.valueOf(childval);
                        break;
                    case "maxpendingdocs":
                        maxpendingdocs = new Integer(childval);
                        break;
                    case "maxpendingbytes":
                        maxpendingbytes = new Integer(childval);
                        break;
                    case "retryenabled":
                        retryenabled = Boolean.valueOf(childval);
                        break;
                    case "retrydelay":
                        retrydelay = new Double(childval);
                        break;
                    case "timeout":
                        timeout = new Double(childval);
                        break;
                    case "route":
                        route = childval;
                        break;
                    case "tracelevel":
                        tracelevel = new Integer(childval);
                        break;
                    case "mbusport":
                        mbusport = new Integer(childval);
                        break;
                    case "docprocchain":
                        docprocChain = childval;
                        break;
                }
            }
            return this;
        }

        /**
         * Returns a feeder options config string of the content of this.
         *
         * @return A config string.
         */
        public FeederConfig.Builder getFeederConfig() {
            FeederConfig.Builder builder = new FeederConfig.Builder();
            if (abortondocumenterror != null) {
                builder.abortondocumenterror(abortondocumenterror);
            }
            if (route != null && route.length() > 0) {
                builder.route(route);
            }
            if (maxpendingdocs != null) {
                builder.maxpendingdocs(maxpendingdocs);
            }
            if (maxpendingbytes != null) {
                builder.maxpendingbytes(maxpendingbytes);
            }
            if (retryenabled != null) {
                builder.retryenabled(retryenabled);
            }
            if (retrydelay != null) {
                builder.retrydelay(retrydelay);
            }
            if (timeout != null) {
                builder.timeout(timeout);
            }
            if (tracelevel != null) {
                builder.tracelevel(tracelevel);
            }
            if (mbusport != null) {
                builder.mbusport(mbusport);
            }
            if (docprocChain != null && docprocChain.length() > 0) {
                builder.docprocchain(docprocChain);
            }
            return builder;
        }
    }
}
