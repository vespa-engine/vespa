// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.vespa.config.content.spooler.SpoolerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.SimpleConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder.DomConfigProducerBuilder;
import com.yahoo.vespa.model.clients.Clients;
import com.yahoo.vespa.model.clients.VespaSpoolMaster;
import com.yahoo.vespa.model.clients.VespaSpooler;
import com.yahoo.vespa.model.clients.VespaSpoolerProducer;
import com.yahoo.vespa.model.clients.VespaSpoolerService;
import com.yahoo.vespaclient.config.FeederConfig;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Clients plugin
 *
 * @author vegardh
 */
public class DomV20ClientsBuilder {

    // The parent docproc plugin to register data with.
    private final Clients clients;

    DomV20ClientsBuilder(Clients clients, String version) {
        if ( ! version.equals("2.0"))
            throw new IllegalArgumentException("Version '" + version + "' of 'clients' not supported.");
        this.clients = clients;
    }

    public void build(Element spec) {
        NodeList children = spec.getElementsByTagName("spoolers");
        for (int i = 0; i < children.getLength(); i++) {
            createSpoolers(clients.getConfigProducer(), (Element) children.item(i), clients);
        }

        children = spec.getElementsByTagName("load-types");
        for (int i = 0; i < children.getLength(); i++) {
            createLoadTypes((Element) children.item(i), clients);
        }
    }

    private void createLoadTypes(Element element, Clients clients) {
        for (Element e : XML.getChildren(element, "type")) {
            String priority = e.getAttribute("default-priority");
            clients.getLoadTypes().addType(e.getAttribute("name"), priority.length() > 0 ? priority : null);
        }
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

    private void createSpoolMasters(SimpleConfigProducer producer, Element element) {
        int i=0;
        for (Element e : XML.getChildren(element, "spoolmaster"))
            new VespaSpoolMasterBuilder(i++).build(producer, e);
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
                        maxpendingdocs = Integer.valueOf(childval);
                        break;
                    case "maxpendingbytes":
                        maxpendingbytes = Integer.valueOf(childval);
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
                        tracelevel = Integer.valueOf(childval);
                        break;
                    case "mbusport":
                        mbusport = Integer.valueOf(childval);
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
