// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.XML;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.docproc.DomDocumentProcessorBuilder;
import com.yahoo.vespa.model.container.http.xml.FilterBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.processing.DomProcessorBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.search.DomFederationSearcherBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.search.DomSearcherBuilder;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;
import com.yahoo.vespa.model.container.http.Filter;
import com.yahoo.vespa.model.container.processing.Processor;
import com.yahoo.vespa.model.container.docproc.DocumentProcessor;
import com.yahoo.vespa.model.container.search.searchchain.Searcher;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Creates component models and component references from xml for a given scope.
 * @author Tony Vaagenes
 */
public class ComponentsBuilder<T extends ChainedComponent<?>> {

    // NOTE: the 'name' string must match the xml tag name for the component in services.
    public static class ComponentType<T extends ChainedComponent<?>> {
        static List<ComponentType<?>> values = new ArrayList<>();
        public static final ComponentType<DocumentProcessor> documentprocessor = new ComponentType<>("documentprocessor", DomDocumentProcessorBuilder.class);
        public static final ComponentType<Searcher<?>> searcher = new ComponentType<>("searcher", DomSearcherBuilder.class);
        public static final ComponentType<Processor> processor = new ComponentType<>("processor", DomProcessorBuilder.class);
        public static final ComponentType<Searcher<?>> federation = new ComponentType<>("federation", DomFederationSearcherBuilder.class);
        public static final ComponentType<Filter> filter = new ComponentType<>("filter", FilterBuilder.class);


        final String name;

        private final Class<? extends VespaDomBuilder.DomConfigProducerBuilder<T>> builderClass;

        private ComponentType(String name, Class<? extends VespaDomBuilder.DomConfigProducerBuilder<T>> builderClass) {
            this.name = name;
            this.builderClass = builderClass;
            values.add(this);
        }

        public VespaDomBuilder.DomConfigProducerBuilder<T> createBuilder() {
            return DomBuilderCreator.create(builderClass);
        }
    }

    private final Set<ComponentSpecification> outerComponentReferences = new LinkedHashSet<>();
    private final List<T> componentDefinitions = new ArrayList<>();
    private final Map<String, ComponentType<?>> componentTypesByComponentName = new LinkedHashMap<>();

    /**
     * @param ancestor The parent config producer
     * @param componentTypes The allowed component types for 'elementContainingComponentElements' - MUST match &lt;T&gt;
     * @param elementsContainingComponentElems All elements containing elements with name matching ComponentType.name
     * @param outerComponentTypeByComponentName Use null if this is the outermost scope, i.e.
     *                                          every component is a definition, not a reference.
     */
    ComponentsBuilder(DeployState deployState,
                      AbstractConfigProducer<?> ancestor,
                      Collection<ComponentType<T>> componentTypes,
                      List<Element> elementsContainingComponentElems,
                      Map<String, ComponentType<?>> outerComponentTypeByComponentName) {

        readComponents(deployState, ancestor, componentTypes, elementsContainingComponentElems, unmodifiable(outerComponentTypeByComponentName));
    }

    private void readComponents(DeployState deployState, AbstractConfigProducer<?> ancestor,
                                Collection<ComponentType<T>> componentTypes,
                                List<Element> elementsContainingComponentElems,
                                Map<String, ComponentType<?>> outerComponentTypeByComponentName) {

        for (ComponentType<T> componentType : componentTypes) {
            for (Element elemContainingComponentElems : elementsContainingComponentElems) {
                for (Element componentElement : XML.getChildren(elemContainingComponentElems, componentType.name)) {
                    readComponent(deployState, ancestor, componentElement, componentType, outerComponentTypeByComponentName);
                }
            }
        }
    }

    private void readComponent(DeployState deployState, AbstractConfigProducer<?> ancestor,
                               Element componentElement,
                               ComponentType<T> componentType,
                               Map<String, ComponentType<?>> outerComponentTypeByComponentName) {

        ComponentSpecification componentSpecification = XmlHelper.getIdRef(componentElement);

        if (outerComponentTypeByComponentName.containsKey(componentSpecification.getName())) {
            readComponentReference(componentElement, componentType, componentSpecification, outerComponentTypeByComponentName);
        } else {
            readComponentDefinition(deployState, ancestor, componentElement, componentType);
        }
    }

    private void readComponentReference(Element componentElement, ComponentType<?> componentType,
                                        ComponentSpecification componentSpecification,
                                        Map<String, ComponentType<?>> outerComponentTypeByComponentName) {

        String componentName = componentSpecification.getName();
        ensureTypesMatch(componentType, outerComponentTypeByComponentName.get(componentName), componentName);
        ensureNotDefinition(componentName, componentElement);
        outerComponentReferences.add(componentSpecification);
    }

    private void readComponentDefinition(DeployState deployState, AbstractConfigProducer<?> ancestor, Element componentElement, ComponentType<T> componentType) {
        T component = componentType.createBuilder().build(deployState, ancestor, componentElement);
        componentDefinitions.add(component);
        updateComponentTypes(component.getComponentId(), componentType);
    }

    private void updateComponentTypes(ComponentId componentId, ComponentType<?> componentType) {
        ComponentType<?> oldType = componentTypesByComponentName.put(componentId.getName(), componentType);
        if (oldType != null) {
            ensureTypesMatch(componentType, oldType, componentId.getName());
        }
    }

    private void ensureNotDefinition(String componentName, Element componentSpec) {
        if (componentSpec.getAttributes().getLength() > 1 || !XML.getChildren(componentSpec).isEmpty())
            throw new IllegalArgumentException("Expecting " + componentName +
                                               " to be a reference to a global component with the same name," +
                                               " so no additional attributes or nested elements are allowed");
    }

    private void ensureTypesMatch(ComponentType<?> type1, ComponentType<?> type2, String componentName) {
        if (!type1.equals(type2)) {
            throw new IllegalArgumentException("Two different types declared for the component with name '" + componentName +
                                               "' (" + type1.name + " != " + type2.name + ").");
        }
    }

    private Map<String, ComponentType<?>> unmodifiable(Map<String, ComponentType<?>> outerComponentTypeByComponentName) {
        return (outerComponentTypeByComponentName != null)?
                Collections.unmodifiableMap(outerComponentTypeByComponentName):
                Collections.emptyMap();
    }

    public Collection<T> getComponentDefinitions() {
        return Collections.unmodifiableCollection(componentDefinitions);
    }

    public Map<String, ComponentType<?>> getComponentTypeByComponentName() {
        return Collections.unmodifiableMap(componentTypesByComponentName);
    }

    public Set<ComponentSpecification> getOuterComponentReferences() {
        return Collections.unmodifiableSet(outerComponentReferences);
    }
}
