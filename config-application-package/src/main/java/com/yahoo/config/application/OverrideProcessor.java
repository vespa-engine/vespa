// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.log.LogLevel;
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
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Handles overrides in a XML document according to the rules defined for multi environment application packages.
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

    private final Environment environment;
    private final RegionName region;
    private static final String ATTR_ID  = "id";
    private static final String ATTR_ENV = "environment";
    private static final String ATTR_REG = "region";

    public OverrideProcessor(Environment environment, RegionName region) {
        this.environment = environment;
        this.region = region;
    }

    public Document process(Document input) throws TransformerException {
        log.log(LogLevel.DEBUG, "Preprocessing overrides with " + environment + "." + region);
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
            child.removeAttributeNS(XmlPreProcessor.deployNamespaceUri, ATTR_ENV);
            child.removeAttributeNS(XmlPreProcessor.deployNamespaceUri, ATTR_REG);
        }
    }

    private Context getParentContext(Element parent, Context context) {
        Set<Environment> environments = context.environments;
        Set<RegionName> regions = context.regions;
        if (environments.isEmpty())
            environments = getEnvironments(parent);
        if (regions.isEmpty())
            regions = getRegions(parent);
        return Context.create(environments, regions);
    }

    /**
     * Prune overrides from parent according to deploy override rules.
     *
     * @param parent             Parent {@link Element} above children.
     * @param children           Children where one {@link Element} will remain as the overriding element
     * @param context            Current context with environment and region.
     */
    private void pruneOverrides(Element parent, List<Element> children, Context context) {
        checkConsistentInheritance(children, context);
        pruneNonMatchingEnvironmentsAndRegions(parent, children);
        retainMostSpecificEnvironmentAndRegion(parent, children, context);
    }

    private void checkConsistentInheritance(List<Element> children, Context context) {
        for (Element child : children) {
            Set<Environment> environments = getEnvironments(child);
            Set<RegionName> regions = getRegions(child);
            if ( ! environments.isEmpty() &&  ! context.environments.isEmpty() && ! context.environments.containsAll(environments)) {
                throw new IllegalArgumentException("Environments in child (" + environments +
                                                   ") are not a subset of those of the parent (" + context.environments + ") at " + child);
            }
            if ( ! regions.isEmpty() && ! context.regions.isEmpty() && ! context.regions.containsAll(regions)) {
                throw new IllegalArgumentException("Regions in child (" + regions +
                                                   ") are not a subset of those of the parent (" + context.regions + ") at " + child);
            }
        }
    }

    /**
     * Prune elements that are not matching our environment and region
     */
    private void pruneNonMatchingEnvironmentsAndRegions(Element parent, List<Element> children) {
        Iterator<Element> elemIt = children.iterator();
        while (elemIt.hasNext()) {
            Element child = elemIt.next();
            if ( ! matches(getEnvironments(child), getRegions(child))) {
                parent.removeChild(child);
                elemIt.remove();
            }
        }
    }
    
    private boolean matches(Set<Environment> elementEnvironments, Set<RegionName> elementRegions) {
        if ( ! elementEnvironments.isEmpty()) { // match environment
            if ( ! elementEnvironments.contains(environment)) return false;
        }

        if ( ! elementRegions.isEmpty()) { // match region
            // match region in multi-region environments only
            if ( environment.isMultiRegion()  && ! elementRegions.contains(region)) return false;

            // explicit region implies multi-region environment
            if ( ! environment.isMultiRegion() && elementEnvironments.isEmpty() ) return false;
        }

        return true;
    }

    /**
     * Find the most specific element and remove all others.
     */
    private void retainMostSpecificEnvironmentAndRegion(Element parent, List<Element> children, Context context) {
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
        Set<Environment> elementEnvironments = hasEnvironment(child) ? getEnvironments(child) : context.environments;
        Set<RegionName> elementRegions = hasRegion(child) ? getRegions(child) : context.regions;
        if ( ! elementEnvironments.isEmpty() && elementEnvironments.contains(environment))
            currentMatch++;
        if ( ! elementRegions.isEmpty() && elementRegions.contains(region))
            currentMatch++;
        return currentMatch;
    }

    /** Called on each element which is selected by matching some override condition */
    private void doElementSpecificProcessingOnOverride(List<Element> elements) {
        // if node capacity is specified explicitly for some env/region we should require that capacity
        elements.forEach(element -> {
            if (element.getTagName().equals("nodes"))
                if (element.getChildNodes().getLength() == 0) // specifies capacity, not a list of nodes
                    element.setAttribute("required", "true");
        });
    }
    
    /**
     * Retains all elements where at least one element is overridden. Removes non-overridden elements from map.
     */
    private void retainOverriddenElements(Map<String, List<Element>> elementsByTagName) {
        Iterator<Map.Entry<String, List<Element>>> it = elementsByTagName.entrySet().iterator();
        while (it.hasNext()) {
            List<Element> elements = it.next().getValue();
            boolean hasOverrides = false;
            for (Element element : elements) {
                if (hasEnvironment(element) || hasRegion(element)) {
                    hasOverrides = true;
                }
            }
            if (!hasOverrides) {
                it.remove();
            }
        }
    }

    private boolean hasRegion(Element element) {
        return element.hasAttributeNS(XmlPreProcessor.deployNamespaceUri, ATTR_REG);
    }

    private boolean hasEnvironment(Element element) {
        return element.hasAttributeNS(XmlPreProcessor.deployNamespaceUri, ATTR_ENV);
    }

    private Set<Environment> getEnvironments(Element element) {
        String env = element.getAttributeNS(XmlPreProcessor.deployNamespaceUri, ATTR_ENV);
        if (env == null || env.isEmpty()) return Collections.emptySet();
        return Arrays.stream(env.split(" ")).map(Environment::from).collect(Collectors.toSet());
    }

    private Set<RegionName> getRegions(Element element) {
        String reg = element.getAttributeNS(XmlPreProcessor.deployNamespaceUri, ATTR_REG);
        if (reg == null || reg.isEmpty()) return Collections.emptySet();
        return Arrays.stream(reg.split(" ")).map(RegionName::from).collect(Collectors.toSet());
    }

    private Map<String, List<Element>> elementsByTagNameAndId(List<Element> children) {
        Map<String, List<Element>> elementsByTagName = new LinkedHashMap<>();
        // Index by tag name
        for (Element child : children) {
            String key = child.getTagName();
            if (child.hasAttribute(ATTR_ID)) {
                key += child.getAttribute(ATTR_ID);
            }
            if (!elementsByTagName.containsKey(key)) {
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

        final ImmutableSet<Environment> environments;

        final ImmutableSet<RegionName> regions;

        private Context(Set<Environment> environments, Set<RegionName> regions) {
            this.environments = ImmutableSet.copyOf(environments);
            this.regions = ImmutableSet.copyOf(regions);
        }

        static Context empty() {
            return new Context(ImmutableSet.of(), ImmutableSet.of());
        }

        public static Context create(Set<Environment> environments, Set<RegionName> regions) {
            return new Context(environments, regions);
        }

    }

}
