// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api.xml;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.DeclaredTest;
import com.yahoo.config.application.api.DeploymentSpec.DeclaredZone;
import com.yahoo.config.application.api.DeploymentSpec.Delay;
import com.yahoo.config.application.api.DeploymentSpec.ParallelSteps;
import com.yahoo.config.application.api.DeploymentSpec.Step;
import com.yahoo.config.application.api.DeploymentSpec.Steps;
import com.yahoo.config.application.api.Endpoint;
import com.yahoo.config.application.api.Notifications;
import com.yahoo.config.application.api.Notifications.Role;
import com.yahoo.config.application.api.Notifications.When;
import com.yahoo.config.application.api.TimeWindow;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.io.IOUtils;
import com.yahoo.text.XML;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.Reader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author bratseth
 */
public class DeploymentSpecXmlReader {

    private static final String instanceTag = "instance";
    private static final String majorVersionTag = "major-version";
    private static final String testTag = "test";
    private static final String stagingTag = "staging";
    private static final String upgradeTag = "upgrade";
    private static final String blockChangeTag = "block-change";
    private static final String prodTag = "prod";
    private static final String regionTag = "region";
    private static final String delayTag = "delay";
    private static final String parallelTag = "parallel";
    private static final String stepsTag = "steps";
    private static final String endpointsTag = "endpoints";
    private static final String endpointTag = "endpoint";
    private static final String notificationsTag = "notifications";

    private static final String idAttribute = "id";
    private static final String athenzServiceAttribute = "athenz-service";
    private static final String athenzDomainAttribute = "athenz-domain";
    private static final String testerFlavorAttribute = "tester-flavor";

    private final boolean validate;

    /** Creates a validating reader */
    public DeploymentSpecXmlReader() {
        this(true);
    }

    /**
     * Creates a deployment spec reader
     *
     * @param validate true to validate the input, false to accept any input which can be unambiguously parsed
     */
    public DeploymentSpecXmlReader(boolean validate) {
        this.validate = validate;
    }

    public DeploymentSpec read(Reader reader) {
        try {
            return read(IOUtils.readAll(reader));
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not read deployment spec", e);
        }
    }

    /** Reads a deployment spec from XML */
    public DeploymentSpec read(String xmlForm) {
        if (DeploymentSpec.empty.xmlForm().equals(xmlForm))
            return DeploymentSpec.empty;

        Element root = XML.getDocument(xmlForm).getDocumentElement();

        List<Step> steps = new ArrayList<>();
        if ( ! containsTag(instanceTag, root)) { // deployment spec skipping explicit instance -> "default" instance
            steps.addAll(readInstanceContent("default", root, new MutableOptional<>(), root));
        }
        else {
            if (XML.getChildren(root).stream().anyMatch(child -> child.getTagName().equals(prodTag)))
                throw new IllegalArgumentException("A deployment spec cannot have both a <prod> tag and an " +
                                                   "<instance> tag under the root: " +
                                                   "Wrap the prod tags inside the appropriate instance");

            for (Element topLevelTag : XML.getChildren(root)) {
                if (topLevelTag.getTagName().equals(instanceTag))
                    steps.addAll(readInstanceContent(topLevelTag.getAttribute(idAttribute), topLevelTag, new MutableOptional<>(), root));
                else
                    steps.addAll(readNonInstanceSteps(topLevelTag, new MutableOptional<>(), root)); // (No global service id here)
            }
        }

        return new DeploymentSpec(steps,
                                  optionalIntegerAttribute(majorVersionTag, root),
                                  stringAttribute(athenzDomainAttribute, root).map(AthenzDomain::from),
                                  stringAttribute(athenzServiceAttribute, root).map(AthenzService::from),
                                  xmlForm);
    }

    /**
     * Reads the content of an (implicit or explicit) instance tag producing an instances step
     *
     * @param instanceNameString a comma-separated list of the names of the instances this is for
     * @param instanceTag the element having the content of this instance
     * @param parentTag the parent of instanceTag (or the same, if this instance is implicitly defined, which means instanceTag is the root)
     * @return the instances specified, one for each instance name element
     */
    private List<DeploymentInstanceSpec> readInstanceContent(String instanceNameString,
                                                             Element instanceTag,
                                                             MutableOptional<String> globalServiceId,
                                                             Element parentTag) {
        if (validate)
            validateTagOrder(instanceTag);

        // Values where the parent may provide a default
        DeploymentSpec.UpgradePolicy upgradePolicy = readUpgradePolicy(instanceTag, parentTag);
        List<DeploymentSpec.ChangeBlocker> changeBlockers = readChangeBlockers(instanceTag, parentTag);
        Optional<AthenzDomain> athenzDomain = stringAttribute(athenzDomainAttribute, instanceTag)
                                                        .or(() -> stringAttribute(athenzDomainAttribute, parentTag))
                                                        .map(AthenzDomain::from);
        Optional<AthenzService> athenzService = stringAttribute(athenzServiceAttribute, instanceTag)
                                                        .or(() -> stringAttribute(athenzServiceAttribute, parentTag))
                                                        .map(AthenzService::from);
        Notifications notifications = readNotifications(instanceTag, parentTag);

        // Values where there is no default
        List<Step> steps = new ArrayList<>();
        for (Element instanceChild : XML.getChildren(instanceTag))
            steps.addAll(readNonInstanceSteps(instanceChild, globalServiceId, instanceChild));
        List<Endpoint> endpoints = readEndpoints(instanceTag);

        // Build and return instances with these values
        return Arrays.stream(instanceNameString.split(","))
                     .map(name -> name.trim())
                     .map(name -> new DeploymentInstanceSpec(InstanceName.from(name),
                                                             steps,
                                                             upgradePolicy,
                                                             changeBlockers,
                                                             globalServiceId.asOptional(),
                                                             athenzDomain,
                                                             athenzService,
                                                             notifications,
                                                             endpoints))
                     .collect(Collectors.toList());
    }

    private List<Step> readSteps(Element stepTag, MutableOptional<String> globalServiceId, Element parentTag) {
        if (stepTag.getTagName().equals(instanceTag))
            return new ArrayList<>(readInstanceContent(stepTag.getAttribute(idAttribute), stepTag, globalServiceId, parentTag));
        else
            return readNonInstanceSteps(stepTag, globalServiceId, parentTag);

    }

    // Consume the given tag as 0-N steps. 0 if it is not a step, >1 if it contains multiple nested steps that should be flattened
    @SuppressWarnings("fallthrough")
    private List<Step> readNonInstanceSteps(Element stepTag, MutableOptional<String> globalServiceId, Element parentTag) {
        Optional<AthenzService> athenzService = stringAttribute(athenzServiceAttribute, stepTag)
                                                        .or(() -> stringAttribute(athenzServiceAttribute, parentTag))
                                                        .map(AthenzService::from);
        Optional<String> testerFlavor = stringAttribute(testerFlavorAttribute, stepTag)
                                                .or(() -> stringAttribute(testerFlavorAttribute, parentTag));

        if (prodTag.equals(stepTag.getTagName()))
            globalServiceId.set(readGlobalServiceId(stepTag));
        else if (readGlobalServiceId(stepTag).isPresent())
            throw new IllegalArgumentException("Attribute 'global-service-id' is only valid on 'prod' tag.");

        switch (stepTag.getTagName()) {
            case testTag:
                if (Stream.iterate(stepTag, Objects::nonNull, Node::getParentNode)
                          .anyMatch(node -> prodTag.equals(node.getNodeName())))
                    return List.of(new DeclaredTest(RegionName.from(XML.getValue(stepTag).trim())));
            case stagingTag:
                return List.of(new DeclaredZone(Environment.from(stepTag.getTagName()), Optional.empty(), false, athenzService, testerFlavor));
            case prodTag: // regions, delay and parallel may be nested within, but we can flatten them
                return XML.getChildren(stepTag).stream()
                                               .flatMap(child -> readNonInstanceSteps(child, globalServiceId, stepTag).stream())
                                               .collect(Collectors.toList());
            case delayTag:
                return List.of(new Delay(Duration.ofSeconds(longAttribute("hours", stepTag) * 60 * 60 +
                                                            longAttribute("minutes", stepTag) * 60 +
                                                            longAttribute("seconds", stepTag))));
            case parallelTag: // regions and instances may be nested within
                return List.of(new ParallelSteps(XML.getChildren(stepTag).stream()
                                                    .flatMap(child -> readSteps(child, globalServiceId, parentTag).stream())
                                                    .collect(Collectors.toList())));
            case stepsTag: // regions and instances may be nested within
                return List.of(new Steps(XML.getChildren(stepTag).stream()
                                            .flatMap(child -> readSteps(child, globalServiceId, parentTag).stream())
                                            .collect(Collectors.toList())));
            case regionTag:
                return List.of(readDeclaredZone(Environment.prod, athenzService, testerFlavor, stepTag));
            default:
                return List.of();
        }
    }

    private boolean containsTag(String childTagName, Element parent) {
        for (Element child : XML.getChildren(parent)) {
            if (child.getTagName().equals(childTagName) || containsTag(childTagName, child))
                return true;
        }
        return false;
    }

    private Notifications readNotifications(Element parent, Element fallbackParent) {
        Element notificationsElement = XML.getChild(parent, notificationsTag);
        if (notificationsElement == null)
            notificationsElement = XML.getChild(fallbackParent, notificationsTag);
        if (notificationsElement == null)
            return Notifications.none();

        When defaultWhen = stringAttribute("when", notificationsElement).map(When::fromValue).orElse(When.failingCommit);
        Map<When, List<String>> emailAddresses = new HashMap<>();
        Map<When, List<Role>> emailRoles = new HashMap<>();
        for (When when : When.values()) {
            emailAddresses.put(when, new ArrayList<>());
            emailRoles.put(when, new ArrayList<>());
        }

        for (Element emailElement : XML.getChildren(notificationsElement, "email")) {
            Optional<String> addressAttribute = stringAttribute("address", emailElement);
            Optional<Role> roleAttribute = stringAttribute("role", emailElement).map(Role::fromValue);
            When when = stringAttribute("when", emailElement).map(When::fromValue).orElse(defaultWhen);
            if (addressAttribute.isPresent() == roleAttribute.isPresent())
                throw new IllegalArgumentException("Exactly one of 'role' and 'address' must be present in 'email' elements.");

            addressAttribute.ifPresent(address -> emailAddresses.get(when).add(address));
            roleAttribute.ifPresent(role -> emailRoles.get(when).add(role));
        }
        return Notifications.of(emailAddresses, emailRoles);
    }

    private List<Endpoint> readEndpoints(Element parent) {
        var endpointsElement = XML.getChild(parent, endpointsTag);
        if (endpointsElement == null)
            return Collections.emptyList();

        var endpoints = new LinkedHashMap<String, Endpoint>();

        for (var endpointElement : XML.getChildren(endpointsElement, endpointTag)) {
            Optional<String> rotationId = stringAttribute("id", endpointElement);
            Optional<String> containerId = stringAttribute("container-id", endpointElement);
            var regions = new HashSet<String>();

            if (containerId.isEmpty()) {
                throw new IllegalArgumentException("Missing 'container-id' from 'endpoint' tag.");
            }

            for (var regionElement : XML.getChildren(endpointElement, "region")) {
                var region = regionElement.getTextContent();
                if (region == null || region.isEmpty() || region.isBlank()) {
                    throw new IllegalArgumentException("Empty 'region' element in 'endpoint' tag.");
                }
                if (regions.contains(region)) {
                    throw new IllegalArgumentException("Duplicate 'region' element in 'endpoint' tag: " + region);
                }
                regions.add(region);
            }

            var endpoint = new Endpoint(rotationId, containerId.get(), regions);
            if (endpoints.containsKey(endpoint.endpointId())) {
                throw new IllegalArgumentException("Duplicate attribute 'id' on 'endpoint': " + endpoint.endpointId());
            }
            endpoints.put(endpoint.endpointId(), endpoint);
        }

        return List.copyOf(endpoints.values());
    }

    /**
     * Imposes some constraints on tag order which are not expressible in the schema
     */
    private void validateTagOrder(Element root) {
        List<String> tags = XML.getChildren(root).stream().map(Element::getTagName).collect(Collectors.toList());
        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).equals(blockChangeTag)) {
                String constraint = "<block-change> must be placed after <test> and <staging> and before <prod>";
                if (containsAfter(i, testTag, tags)) throw new IllegalArgumentException(constraint);
                if (containsAfter(i, stagingTag, tags)) throw new IllegalArgumentException(constraint);
                if (containsBefore(i, prodTag, tags)) throw new IllegalArgumentException(constraint);
            }
        }
    }

    private boolean containsAfter(int i, String item, List<String> items) {
        return items.subList(i + 1, items.size()).contains(item);
    }

    private boolean containsBefore(int i, String item, List<String> items) {
        return items.subList(0, i).contains(item);
    }

    /**
     * Returns the given attribute as an integer, or 0 if it is not present
     */
    private long longAttribute(String attributeName, Element tag) {
        String value = tag.getAttribute(attributeName);
        if (value == null || value.isEmpty()) return 0;
        try {
            return Long.parseLong(value);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected an integer for attribute '" + attributeName +
                                               "' but got '" + value + "'");
        }
    }

    /**
     * Returns the given attribute as an integer, or 0 if it is not present
     */
    private Optional<Integer> optionalIntegerAttribute(String attributeName, Element tag) {
        String value = tag.getAttribute(attributeName);
        if (value == null || value.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(value));
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected an integer for attribute '" + attributeName +
                                               "' but got '" + value + "'");
        }
    }

    /**
     * Returns the given attribute as a string, or Optional.empty if it is not present or empty
     */
    private Optional<String> stringAttribute(String attributeName, Element tag) {
        String value = tag.getAttribute(attributeName);
        return Optional.ofNullable(value).filter(s -> !s.equals(""));
    }

    private DeclaredZone readDeclaredZone(Environment environment, Optional<AthenzService> athenzService,
                                          Optional<String> testerFlavor, Element regionTag) {
        return new DeclaredZone(environment, Optional.of(RegionName.from(XML.getValue(regionTag).trim())),
                                readActive(regionTag), athenzService, testerFlavor);
    }

    private Optional<String> readGlobalServiceId(Element environmentTag) {
        String globalServiceId = environmentTag.getAttribute("global-service-id");
        if (globalServiceId == null || globalServiceId.isEmpty()) return Optional.empty();
        return Optional.of(globalServiceId);
    }

    private List<DeploymentSpec.ChangeBlocker> readChangeBlockers(Element parent, Element globalBlockersParent) {
        List<DeploymentSpec.ChangeBlocker> changeBlockers = new ArrayList<>();
        if (globalBlockersParent != parent) {
            for (Element tag : XML.getChildren(globalBlockersParent, blockChangeTag))
                changeBlockers.add(readChangeBlocker(tag));
        }
        for (Element tag : XML.getChildren(parent, blockChangeTag))
            changeBlockers.add(readChangeBlocker(tag));
        return Collections.unmodifiableList(changeBlockers);
    }

    private DeploymentSpec.ChangeBlocker readChangeBlocker(Element tag) {
        boolean blockVersions = trueOrMissing(tag.getAttribute("version"));
        boolean blockRevisions = trueOrMissing(tag.getAttribute("revision"));

        String daySpec = tag.getAttribute("days");
        String hourSpec = tag.getAttribute("hours");
        String zoneSpec = tag.getAttribute("time-zone");
        if (zoneSpec.isEmpty()) zoneSpec = "UTC"; // default
        return new DeploymentSpec.ChangeBlocker(blockRevisions, blockVersions,
                                                TimeWindow.from(daySpec, hourSpec, zoneSpec));
    }

    /** Returns true if the given value is "true", or if it is missing */
    private boolean trueOrMissing(String value) {
        return value == null || value.isEmpty() || value.equals("true");
    }

    private DeploymentSpec.UpgradePolicy readUpgradePolicy(Element parent, Element fallbackParent) {
        Element upgradeElement = XML.getChild(parent, upgradeTag);
        if (upgradeElement == null)
            upgradeElement = XML.getChild(fallbackParent, upgradeTag);
        if (upgradeElement == null)
            return DeploymentSpec.UpgradePolicy.defaultPolicy;

        String policy = upgradeElement.getAttribute("policy");
        switch (policy) {
            case "canary": return DeploymentSpec.UpgradePolicy.canary;
            case "default": return DeploymentSpec.UpgradePolicy.defaultPolicy;
            case "conservative": return DeploymentSpec.UpgradePolicy.conservative;
            default: throw new IllegalArgumentException("Illegal upgrade policy '" + policy + "': " +
                                                        "Must be one of " + Arrays.toString(DeploymentSpec.UpgradePolicy.values()));
        }
    }

    private boolean readActive(Element regionTag) {
        String activeValue = regionTag.getAttribute("active");
        if ("true".equals(activeValue)) return true;
        if ("false".equals(activeValue)) return false;
        throw new IllegalArgumentException("Region tags must have an 'active' attribute set to 'true' or 'false' " +
                                           "to control whether the region should receive production traffic");
    }

    private static class MutableOptional<T> {

        private Optional<T> value = Optional.empty();

        public void set(Optional<T> value) { this.value = value; }

        public Optional<T> asOptional() { return value; }

    }

}
