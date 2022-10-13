// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import com.yahoo.text.XML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import javax.xml.transform.TransformerException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Handles overrides in a XML document according to the rules defined for multi-environment application packages.
 *
 * Rules:
 *
 * 1. A directive specifying both environment and region will override a more generic directive specifying only one of them
 * 2. Directives are inherited in child elements
 * 3. When multiple XML elements with the same name is specified (i.e. when specifying search or docproc chains),
 *    the id attribute of the element is used together with the element name when applying directives
 *
 * @author Ulf Lilleengen
 */
class OverrideProcessor implements PreProcessor {

    private static final Logger log = Logger.getLogger(OverrideProcessor.class.getName());

    private final InstanceName instance;
    private final Environment environment;
    private final RegionName region;
    private final Tags tags;

    private static final String ID_ATTRIBUTE = "id";
    private static final String INSTANCE_ATTRIBUTE = "instance";
    private static final String ENVIRONMENT_ATTRIBUTE = "environment";
    private static final String REGION_ATTRIBUTE = "region";
    private static final String TAGS_ATTRIBUTE = "tags";

    public OverrideProcessor(InstanceName instance, Environment environment, RegionName region, Tags tags) {
        this.instance = instance;
        this.environment = environment;
        this.region = region;
        this.tags = tags;
    }

    public Document process(Document input) throws TransformerException {
        log.log(Level.FINE, () -> "Preprocessing overrides with " + environment + "." + region);
        Document ret = Xml.copyDocument(input);
        Element root = ret.getDocumentElement();
        applyOverrides(root, Context.empty());
        return ret;
    }

    private void applyOverrides(Element parent, Context context) {
        context = getParentContext(parent, context);

        Map<String, List<Element>> elementsByTagName = elementsByTagNameAndId(XML.getChildren(parent));

        retainOverriddenElements(elementsByTagName);

        // For each tag name, prune overrides
        for (Map.Entry<String, List<Element>> entry : elementsByTagName.entrySet()) {
            pruneOverrides(parent, entry.getValue(), context);
        }

        // Repeat for remaining children;
        for (Element child : XML.getChildren(parent)) {
            applyOverrides(child, context);
            // Remove attributes
            child.removeAttributeNS(XmlPreProcessor.deployNamespaceUri, INSTANCE_ATTRIBUTE);
            child.removeAttributeNS(XmlPreProcessor.deployNamespaceUri, ENVIRONMENT_ATTRIBUTE);
            child.removeAttributeNS(XmlPreProcessor.deployNamespaceUri, REGION_ATTRIBUTE);
            child.removeAttributeNS(XmlPreProcessor.deployNamespaceUri, TAGS_ATTRIBUTE);
        }
    }

    private Context getParentContext(Element parent, Context context) {
        Set<InstanceName> instances = context.instances;
        Set<Environment> environments = context.environments;
        Set<RegionName> regions = context.regions;
        Tags tags = context.tags;
        if (instances.isEmpty())
            instances = getInstances(parent);
        if (environments.isEmpty())
            environments = getEnvironments(parent);
        if (regions.isEmpty())
            regions = getRegions(parent);
        if (tags.isEmpty())
            tags = getTags(parent);
        return Context.create(instances, environments, regions, tags);
    }

    /**
     * Prune overrides from parent according to deploy override rules.
     *
     * @param parent parent {@link Element} above children.
     * @param children children where one {@link Element} will remain as the overriding element
     * @param context current context with instance, environment and region.
     */
    private void pruneOverrides(Element parent, List<Element> children, Context context) {
        checkConsistentInheritance(children, context);
        pruneNonMatching(parent, children);
        retainMostSpecific(parent, children, context);
    }

    private void checkConsistentInheritance(List<Element> children, Context context) {
        for (Element child : children) {
            Set<InstanceName> instances = getInstances(child);
            if ( ! instances.isEmpty() &&  ! context.instances.isEmpty() && ! context.instances.containsAll(instances)) {
                throw new IllegalArgumentException("Instances in child (" + instances +
                                                   ") are not a subset of those of the parent (" + context.instances + ") at " + child);
            }

            Set<Environment> environments = getEnvironments(child);
            if ( ! environments.isEmpty() &&  ! context.environments.isEmpty() && ! context.environments.containsAll(environments)) {
                throw new IllegalArgumentException("Environments in child (" + environments +
                                                   ") are not a subset of those of the parent (" + context.environments + ") at " + child);
            }

            Set<RegionName> regions = getRegions(child);
            if ( ! regions.isEmpty() && ! context.regions.isEmpty() && ! context.regions.containsAll(regions)) {
                throw new IllegalArgumentException("Regions in child (" + regions +
                                                   ") are not a subset of those of the parent (" + context.regions + ") at " + child);
            }

            Tags tags = getTags(child);
            if ( ! tags.isEmpty() &&  ! context.tags.isEmpty() && ! context.tags.containsAll(tags)) {
                throw new IllegalArgumentException("Tags in child (" + environments +
                                                   ") are not a subset of those of the parent (" + context.tags + ") at " + child);
            }
        }
    }

    /** Prune elements that are not matching our environment and region. */
    private void pruneNonMatching(Element parent, List<Element> children) {
        Iterator<Element> elemIt = children.iterator();
        while (elemIt.hasNext()) {
            Element child = elemIt.next();
            if ( ! matches(getInstances(child), getEnvironments(child), getRegions(child), getTags(child))) {
                parent.removeChild(child);
                elemIt.remove();
            }
        }
    }
    
    private boolean matches(Set<InstanceName> elementInstances,
                            Set<Environment> elementEnvironments,
                            Set<RegionName> elementRegions,
                            Tags elementTags) {
        if ( ! elementInstances.isEmpty()) { // match instance
            if ( ! elementInstances.contains(instance)) return false;
        }

        if ( ! elementEnvironments.isEmpty()) { // match environment
            if ( ! elementEnvironments.contains(environment)) return false;
        }

        if ( ! elementRegions.isEmpty()) { // match region
            // match region in multi-region environments only
            if ( environment.isMultiRegion()  && ! elementRegions.contains(region)) return false;

            // explicit region implies multi-region environment
            if ( ! environment.isMultiRegion() && elementEnvironments.isEmpty() ) return false;
        }

        if ( ! elementTags.isEmpty()) { // match tags
            if ( ! elementTags.intersects(tags)) return false;
        }

        return true;
    }

    /** Find the most specific element and remove all others. */
    private void retainMostSpecific(Element parent, List<Element> children, Context context) {
        // Keep track of elements with highest number of matches (might be more than one element with same tag, need a list)
        List<Element> bestMatches = new ArrayList<>();
        int bestMatch = 0;
        for (Element child : children) {
            bestMatch = updateBestMatches(bestMatches, child, bestMatch, context);
        }
        if (bestMatch > 0) { // there was a region/environment specific override
            doElementSpecificProcessingOnOverride(bestMatches);
            for (Element child : children) {
                if ( ! bestMatches.contains(child)) {
                    parent.removeChild(child);
                }
            }
        }
    }

    private int updateBestMatches(List<Element> bestMatches, Element child, int bestMatch, Context context) {
        int overrideCount = getNumberOfOverrides(child, context);
        if (overrideCount >= bestMatch) {
            if (overrideCount > bestMatch)
                bestMatches.clear();

            bestMatches.add(child);
            return overrideCount;
        } else {
            return bestMatch;
        }
    }

    private int getNumberOfOverrides(Element child, Context context) {
        int currentMatch = 0;
        Set<InstanceName> elementInstances = hasInstance(child) ? getInstances(child) : context.instances;
        Set<Environment> elementEnvironments = hasEnvironment(child) ? getEnvironments(child) : context.environments;
        Set<RegionName> elementRegions = hasRegion(child) ? getRegions(child) : context.regions;
        Tags elementTags = hasTag(child) ? getTags(child) : context.tags;
        if ( ! elementInstances.isEmpty() && elementInstances.contains(instance))
            currentMatch++;
        if ( ! elementEnvironments.isEmpty() && elementEnvironments.contains(environment))
            currentMatch++;
        if ( ! elementRegions.isEmpty() && elementRegions.contains(region))
            currentMatch++;
        if ( elementTags.intersects(tags))
            currentMatch++;
        return currentMatch;
    }

    /** Called on each element which is selected by matching some override condition */
    private void doElementSpecificProcessingOnOverride(List<Element> elements) {
        // if node capacity is specified explicitly for some combination we should require that capacity
        elements.forEach(element -> {
            if (element.getTagName().equals("nodes"))
                if (!hasChildWithTagName(element, "node")) // specifies capacity, not a list of nodes
                    element.setAttribute("required", "true");
        });
    }

    private static boolean hasChildWithTagName(Element element, String childName) {
        for (var child : XML.getChildren(element)) {
            if (child.getTagName().equals(childName))
                return true;
        }

        return false;
    }

    /** Retains all elements where at least one element is overridden. Removes non-overridden elements from map. */
    private void retainOverriddenElements(Map<String, List<Element>> elementsByTagName) {
        Iterator<Map.Entry<String, List<Element>>> it = elementsByTagName.entrySet().iterator();
        while (it.hasNext()) {
            List<Element> elements = it.next().getValue();
            boolean hasOverrides = false;
            for (Element element : elements) {
                if (hasInstance(element) || hasEnvironment(element) || hasRegion(element) || hasTag(element)) {
                    hasOverrides = true;
                }
            }
            if (!hasOverrides) {
                it.remove();
            }
        }
    }

    private boolean hasInstance(Element element) {
        return element.hasAttributeNS(XmlPreProcessor.deployNamespaceUri, INSTANCE_ATTRIBUTE);
    }

    private boolean hasRegion(Element element) {
        return element.hasAttributeNS(XmlPreProcessor.deployNamespaceUri, REGION_ATTRIBUTE);
    }

    private boolean hasEnvironment(Element element) {
        return element.hasAttributeNS(XmlPreProcessor.deployNamespaceUri, ENVIRONMENT_ATTRIBUTE);
    }

    private boolean hasTag(Element element) {
        return element.hasAttributeNS(XmlPreProcessor.deployNamespaceUri, TAGS_ATTRIBUTE);
    }

    private Set<InstanceName> getInstances(Element element) {
        String instance = element.getAttributeNS(XmlPreProcessor.deployNamespaceUri, INSTANCE_ATTRIBUTE);
        if (instance == null || instance.isEmpty()) return  Set.of();
        return Arrays.stream(instance.split(" ")).map(InstanceName::from).collect(Collectors.toSet());
    }

    private Set<Environment> getEnvironments(Element element) {
        String env = element.getAttributeNS(XmlPreProcessor.deployNamespaceUri, ENVIRONMENT_ATTRIBUTE);
        if (env == null || env.isEmpty()) return Set.of();
        return Arrays.stream(env.split(" ")).map(Environment::from).collect(Collectors.toSet());
    }

    private Set<RegionName> getRegions(Element element) {
        String reg = element.getAttributeNS(XmlPreProcessor.deployNamespaceUri, REGION_ATTRIBUTE);
        if (reg == null || reg.isEmpty()) return Set.of();
        return Arrays.stream(reg.split(" ")).map(RegionName::from).collect(Collectors.toSet());
    }

    private Tags getTags(Element element) {
        String env = element.getAttributeNS(XmlPreProcessor.deployNamespaceUri, TAGS_ATTRIBUTE);
        if (env == null || env.isEmpty()) return Tags.empty();
        return Tags.fromString(env);
    }

    private Map<String, List<Element>> elementsByTagNameAndId(List<Element> children) {
        Map<String, List<Element>> elementsByTagName = new LinkedHashMap<>();
        // Index by tag name
        for (Element child : children) {
            String key = child.getTagName();
            if (child.hasAttribute(ID_ATTRIBUTE)) {
                key += child.getAttribute(ID_ATTRIBUTE);
            }
            if ( ! elementsByTagName.containsKey(key)) {
                elementsByTagName.put(key, new ArrayList<>());
            }
            elementsByTagName.get(key).add(child);
        }
        return elementsByTagName;
    }

    // For debugging
    private static String getPrintableElement(Element element) {
        StringBuilder sb = new StringBuilder(element.getTagName());
        final NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            sb.append(" ").append(attributes.item(i).getNodeName());
        }
        return sb.toString();
    }

    // For debugging
    private static String getPrintableElementRecursive(Element element) {
        StringBuilder sb = new StringBuilder();
        sb.append(element.getTagName());
        final NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            sb.append(" ")
              .append(attributes.item(i).getNodeName())
              .append("=")
              .append(attributes.item(i).getNodeValue());
        }
        final List<Element> children = XML.getChildren(element);
        if (children.size() > 0) {
            sb.append("\n");
            for (Element e : children)
                sb.append("\t").append(getPrintableElementRecursive(e));
        }
        return sb.toString();
    }

    /**
     * Represents environment and region in a given context.
     */
    private static final class Context {

        final Set<InstanceName> instances;
        final Set<Environment> environments;
        final Set<RegionName> regions;
        final Tags tags;

        private Context(Set<InstanceName> instances,
                        Set<Environment> environments,
                        Set<RegionName> regions,
                        Tags tags) {
            this.instances = Set.copyOf(instances);
            this.environments = Set.copyOf(environments);
            this.regions = Set.copyOf(regions);
            this.tags = tags;
        }

        static Context empty() {
            return new Context(Set.of(), Set.of(), Set.of(), Tags.empty());
        }

        public static Context create(Set<InstanceName> instances,
                                     Set<Environment> environments,
                                     Set<RegionName> regions,
                                     Tags tags) {
            return new Context(instances, environments, regions, tags);
        }

    }

}
