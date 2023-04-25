// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.graph.ModelGraphBuilder;
import com.yahoo.config.model.graph.ModelNode;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.model.provision.HostsXmlProvisioner;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.builder.VespaModelBuilder;
import com.yahoo.vespa.model.content.Content;
import com.yahoo.vespa.model.routing.Routing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A collection of config model instances owned by a system model
 *
 * @author gjoranv
 */
public class ConfigModelRepo implements ConfigModelRepoAdder, Serializable, Iterable<ConfigModel> {

    private static final long serialVersionUID = 1L;

    private static final Logger log = Logger.getLogger(ConfigModelRepo.class.getPackage().toString());

    private final Map<String,ConfigModel> configModelMap = new TreeMap<>();
    private final List<ConfigModel> configModels = new ArrayList<>();

    /**
     * Returns a config model for a given id
     *
     * @param id the id of the model to return
     * @return the model, or none if a model with this id is not present in this
     */
    public ConfigModel get(String id) {
        return configModelMap.get(id);
    }

    /** Adds a new config model instance in this */
    @Override
    public void add(ConfigModel model) {
        configModelMap.put(model.getId(), model);
        configModels.add(model);
    }

    /** Returns the models in this as an iterator */
    public Iterator<ConfigModel> iterator() {
        return configModels.iterator();
    }

    /** Returns a read-only view of the config model instances of this */
    public Map<String,ConfigModel> asMap() { return Collections.unmodifiableMap(configModelMap); }

    /** Initialize part 1.: Reads the config models used in the application package. */
    public void readConfigModels(DeployState deployState,
                                 VespaModel vespaModel,
                                 VespaModelBuilder builder,
                                 ApplicationConfigProducerRoot root,
                                 ConfigModelRegistry configModelRegistry) throws IOException {
        Element userServicesElement = getServicesFromApp(deployState.getApplicationPackage());
        readConfigModels(root, userServicesElement, deployState, vespaModel, configModelRegistry);
        builder.postProc(deployState, root, this);
    }

    private Element getServicesFromApp(ApplicationPackage applicationPackage) throws IOException {
        try (Reader servicesFile = applicationPackage.getServices()) {
            return getServicesFromReader(servicesFile);
        }
    }

    /**
     * If the top level is &lt;services&gt;, it contains a list of services elements,
     * otherwise, the top level tag is a single service.
     */
    private List<Element> getServiceElements(Element servicesRoot) {
        if (servicesRoot.getTagName().equals("services"))
            return XML.getChildren(servicesRoot);
        List<Element> singleServiceList = new ArrayList<>(1);
        singleServiceList.add(servicesRoot);
        return singleServiceList;
    }

    /**
     * Creates all the config models specified in the given XML element and
     * passes their respective XML node as parameter.
     *
     * @param root The Root to set as parent for all plugins
     * @param servicesRoot XML root node of the services file
     */
    private void readConfigModels(ApplicationConfigProducerRoot root,
                                  Element servicesRoot,
                                  DeployState deployState,
                                  VespaModel vespaModel,
                                  ConfigModelRegistry configModelRegistry) {
        final Map<ConfigModelBuilder, List<Element>> model2Element = new LinkedHashMap<>();
        ModelGraphBuilder graphBuilder = new ModelGraphBuilder();

        final List<Element> children = getServiceElements(servicesRoot);

        if (XML.getChild(servicesRoot, "admin") == null)
            children.add(getImplicitAdmin(deployState));

        for (Element servicesElement : children) {
            String tagName = servicesElement.getTagName();
            if (tagName.equals("legacy")) {
                // for enabling legacy features from old vespa versions
                continue;
            }
            if (tagName.equals("config")) {
                // Top level config, mainly to be used by the Vespa team.
                continue;
            }

            String tagVersion = servicesElement.getAttribute("version");
            ConfigModelId xmlId = ConfigModelId.fromNameAndVersion(tagName, tagVersion);

            Collection<ConfigModelBuilder> builders = configModelRegistry.resolve(xmlId);

            if (builders.isEmpty())
                throw new IllegalArgumentException("Could not resolve tag <" + tagName + " version=\"" + tagVersion + "\"> to a config model component");

            for (ConfigModelBuilder builder : builders) {
                if ( ! model2Element.containsKey(builder)) {
                    model2Element.put(builder, new ArrayList<>());
                    graphBuilder.addBuilder(builder);
                }
                model2Element.get(builder).add(servicesElement);
            }
        }

        for (ModelNode node : graphBuilder.build().topologicalSort())
            buildModels(node, getApplicationType(servicesRoot), deployState, vespaModel, root, model2Element.get(node.builder));
        for (ConfigModel model : configModels)
            model.initialize(ConfigModelRepo.this); // XXX deprecated
    }

    private ApplicationType getApplicationType(Element servicesRoot) {
        return XmlHelper.getOptionalAttribute(servicesRoot, "application-type")
                .map(ApplicationType::fromString)
                .orElse(ApplicationType.DEFAULT);
    }

    private Element getServicesFromReader(Reader reader) {
        Document doc = XmlHelper.getDocument(reader);
        return doc.getDocumentElement();
    }

    private void buildModels(ModelNode node,
                             ApplicationType applicationType,
                             DeployState deployState,
                             VespaModel vespaModel,
                             TreeConfigProducer<AnyConfigProducer> parent,
                             List<Element> elements) {
        for (Element servicesElement : elements) {
            ConfigModel model = buildModel(node, applicationType, deployState, vespaModel, parent, servicesElement);
            if (model.isServing())
                add(model);
        }
    }

    private ConfigModel buildModel(ModelNode node,
                                   ApplicationType applicationType,
                                   DeployState deployState,
                                   VespaModel vespaModel,
                                   TreeConfigProducer<AnyConfigProducer> parent,
                                   Element servicesElement) {
        ConfigModelBuilder builder = node.builder;
        ConfigModelContext context = ConfigModelContext.create(applicationType, deployState, vespaModel, this, parent, getIdString(servicesElement));
        return builder.build(node, servicesElement, context);
    }

    private static String getIdString(Element spec) {
        String idString = XmlHelper.getIdString(spec);
        if (idString == null || idString.isEmpty()) {
            idString = spec.getTagName();
        }
        return idString;
    }

    /**
     * Initialize part 2.:
     * Prepare all config models for starting. Must be called after plugins are loaded and frozen.
     */
    public void prepareConfigModels(DeployState deployState) {
        for (ConfigModel model : configModels) {
            model.prepare(this, deployState);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends ConfigModel> List<T> getModels(Class<T> modelClass) {
        List<T> modelsOfModelClass = new ArrayList<>();

        for (ConfigModel model : configModels) {
            if (modelClass.isInstance(model))
                modelsOfModelClass.add((T)model);
        }
        return modelsOfModelClass;
    }

    public Routing getRouting() {
        for (ConfigModel m : configModels) {
            if (m instanceof Routing) {
                return (Routing)m;
            }
        }
        return null;
    }

    public Content getContent() {
        for (ConfigModel m : configModels) {
            if (m instanceof Content) {
                return (Content)m;
            }
        }
        return null;
    }

    // TODO: Doctoring on the XML is the wrong level for this. We should be able to mark a model as default instead   -Jon
    private static Element getImplicitAdmin(DeployState deployState) {
        String defaultAdminElement = deployState.isHosted() ? getImplicitAdminV4() : getImplicitAdminV2();
        log.log(Level.FINE, () -> "No <admin> defined, using " + defaultAdminElement);
        return XmlHelper.getDocument(new StringReader(defaultAdminElement)).getDocumentElement();
    }

    private static String getImplicitAdminV2() {
        return "<admin version='2.0'>\n" +
               "  <adminserver hostalias='" + HostsXmlProvisioner.IMPLICIT_ADMIN_HOSTALIAS + "'/>\n" +
               "</admin>\n";
    }

    private static String getImplicitAdminV4() {
        return "<admin version='4.0'>\n" +
                "  <nodes count='1' />\n" +
                "</admin>\n";
    }
}
