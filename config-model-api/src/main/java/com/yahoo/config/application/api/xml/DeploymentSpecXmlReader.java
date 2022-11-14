// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api.xml;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.DeclaredTest;
import com.yahoo.config.application.api.DeploymentSpec.DeclaredZone;
import com.yahoo.config.application.api.DeploymentSpec.Delay;
import com.yahoo.config.application.api.DeploymentSpec.DeprecatedElement;
import com.yahoo.config.application.api.DeploymentSpec.ParallelSteps;
import com.yahoo.config.application.api.DeploymentSpec.RevisionChange;
import com.yahoo.config.application.api.DeploymentSpec.RevisionTarget;
import com.yahoo.config.application.api.DeploymentSpec.Step;
import com.yahoo.config.application.api.DeploymentSpec.Steps;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.config.application.api.DeploymentSpec.UpgradeRollout;
import com.yahoo.config.application.api.Endpoint;
import com.yahoo.config.application.api.Notifications;
import com.yahoo.config.application.api.Notifications.Role;
import com.yahoo.config.application.api.Notifications.When;
import com.yahoo.config.application.api.TimeWindow;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import com.yahoo.io.IOUtils;
import com.yahoo.text.XML;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.Reader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author bratseth
 */
public class DeploymentSpecXmlReader {

    private static final String deploymentTag = "deployment";
    private static final String instanceTag = "instance";
    private static final String tagsTag = "tags";
    private static final String testTag = "test";
    private static final String stagingTag = "staging";
    private static final String devTag = "dev";
    private static final String perfTag = "perf";
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
    private static final String majorVersionAttribute = "major-version";
    private static final String globalServiceIdAttribute = "global-service-id";
    private static final String cloudAccountAttribute = "cloud-account";

    private final boolean validate;
    private final Clock clock;
    private final List<DeprecatedElement> deprecatedElements = new ArrayList<>();

    /**
     * Create a deployment spec reader
     * @param validate  true to validate the input, false to accept any input which can be unambiguously parsed
     * @param clock     clock to use when validating time constraints
     */
    public DeploymentSpecXmlReader(boolean validate, Clock clock) {
        this.validate = validate;
        this.clock = clock;
    }

    public DeploymentSpecXmlReader() {
        this(true);
    }

    public DeploymentSpecXmlReader(boolean validate) {
        this(validate, Clock.systemUTC());
    }

    /** Reads a deployment spec from given reader */
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
        deprecatedElements.clear();
        Element root = XML.getDocument(xmlForm).getDocumentElement();
        if ( ! root.getTagName().equals(deploymentTag))
            illegal("The root tag must be <deployment>");

        if (isEmptySpec(root))
            return DeploymentSpec.empty;

        List<Step> steps = new ArrayList<>();
        List<Endpoint> applicationEndpoints = List.of();
        if ( ! containsTag(instanceTag, root)) { // deployment spec skipping explicit instance -> "default" instance
            steps.addAll(readInstanceContent("default", root, new HashMap<>(), root));
        }
        else {
            if (XML.getChildren(root).stream().anyMatch(child -> child.getTagName().equals(prodTag)))
                illegal("A deployment spec cannot have both a <prod> tag and an " +
                        "<instance> tag under the root: " +
                        "Wrap the prod tags inside the appropriate instance");

            for (Element child : XML.getChildren(root)) {
                String tagName = child.getTagName();
                if (tagName.equals(instanceTag)) {
                    steps.addAll(readInstanceContent(child.getAttribute(idAttribute), child, new HashMap<>(), root));
                } else {
                    steps.addAll(readNonInstanceSteps(child, new HashMap<>(), root)); // (No global service id here)
                }
            }
            applicationEndpoints = readEndpoints(root, Optional.empty(), steps);
        }

        return new DeploymentSpec(steps,
                                  optionalIntegerAttribute(majorVersionAttribute, root),
                                  stringAttribute(athenzDomainAttribute, root).map(AthenzDomain::from),
                                  stringAttribute(athenzServiceAttribute, root).map(AthenzService::from),
                                  stringAttribute(cloudAccountAttribute, root).map(CloudAccount::from),
                                  applicationEndpoints,
                                  xmlForm,
                                  deprecatedElements);
    }

    /**
     * Reads the content of an (implicit or explicit) instance tag producing an instances step
     *
     * @param instanceNameString a comma-separated list of the names of the instances this is for
     * @param instanceElement the element having the content of this instance
     * @param parentTag the parent of instanceTag (or the same, if this instance is implicitly defined, which means instanceTag is the root)
     * @return the instances specified, one for each instance name element
     */
    private List<DeploymentInstanceSpec> readInstanceContent(String instanceNameString,
                                                             Element instanceElement,
                                                             Map<String, String> prodAttributes,
                                                             Element parentTag) {
        if (instanceNameString.isBlank())
            illegal("<instance> attribute 'id' must be specified, and not be blank");

        // If this is an absolutely empty instance, or the implicit "default" instance but without content, ignore it
        if (XML.getChildren(instanceElement).isEmpty() && (instanceElement.getAttributes().getLength() == 0 || instanceElement == parentTag))
            return List.of();

        if (validate)
            validateTagOrder(instanceElement);

        // Values where the parent may provide a default
        DeploymentSpec.UpgradePolicy upgradePolicy = getWithFallback(instanceElement, parentTag, upgradeTag, "policy", this::readUpgradePolicy, UpgradePolicy.defaultPolicy);
        DeploymentSpec.RevisionTarget revisionTarget = getWithFallback(instanceElement, parentTag, upgradeTag, "revision-target", this::readRevisionTarget, RevisionTarget.latest);
        DeploymentSpec.RevisionChange revisionChange = getWithFallback(instanceElement, parentTag, upgradeTag, "revision-change", this::readRevisionChange, RevisionChange.whenFailing);
        DeploymentSpec.UpgradeRollout upgradeRollout = getWithFallback(instanceElement, parentTag, upgradeTag, "rollout", this::readUpgradeRollout, UpgradeRollout.separate);
        int minRisk = getWithFallback(instanceElement, parentTag, upgradeTag, "min-risk", Integer::parseInt, 0);
        int maxRisk = getWithFallback(instanceElement, parentTag, upgradeTag, "max-risk", Integer::parseInt, 0);
        int maxIdleHours = getWithFallback(instanceElement, parentTag, upgradeTag, "max-idle-hours", Integer::parseInt, 8);
        List<DeploymentSpec.ChangeBlocker> changeBlockers = readChangeBlockers(instanceElement, parentTag);
        Optional<AthenzService> athenzService = mostSpecificAttribute(instanceElement, athenzServiceAttribute).map(AthenzService::from);
        Optional<CloudAccount> cloudAccount = mostSpecificAttribute(instanceElement, cloudAccountAttribute).map(CloudAccount::from);
        Notifications notifications = readNotifications(instanceElement, parentTag);

        // Values where there is no default
        Tags tags = XML.attribute(tagsTag, instanceElement).map(value -> Tags.fromString(value)).orElse(Tags.empty());
        List<Step> steps = new ArrayList<>();
        for (Element instanceChild : XML.getChildren(instanceElement))
            steps.addAll(readNonInstanceSteps(instanceChild, prodAttributes, instanceChild));
        List<Endpoint> endpoints = readEndpoints(instanceElement, Optional.of(instanceNameString), steps);

        // Build and return instances with these values
        Instant now = clock.instant();
        return Arrays.stream(instanceNameString.split(","))
                     .map(name -> name.trim())
                     .map(name -> new DeploymentInstanceSpec(InstanceName.from(name),
                                                             tags,
                                                             steps,
                                                             upgradePolicy,
                                                             revisionTarget,
                                                             revisionChange,
                                                             upgradeRollout,
                                                             minRisk, maxRisk, maxIdleHours,
                                                             changeBlockers,
                                                             Optional.ofNullable(prodAttributes.get(globalServiceIdAttribute)),
                                                             athenzService,
                                                             cloudAccount,
                                                             notifications,
                                                             endpoints,
                                                             now))
                     .collect(Collectors.toList());
    }

    private List<Step> readSteps(Element stepTag, Map<String, String> prodAttributes, Element parentTag) {
        if (stepTag.getTagName().equals(instanceTag))
            return new ArrayList<>(readInstanceContent(stepTag.getAttribute(idAttribute), stepTag, prodAttributes, parentTag));
        else
            return readNonInstanceSteps(stepTag, prodAttributes, parentTag);

    }

    // Consume the given tag as 0-N steps. 0 if it is not a step, >1 if it contains multiple nested steps that should be flattened
    private List<Step> readNonInstanceSteps(Element stepTag, Map<String, String> prodAttributes, Element parentTag) {
        Optional<AthenzService> athenzService = mostSpecificAttribute(stepTag, athenzServiceAttribute).map(AthenzService::from);
        Optional<String> testerFlavor = mostSpecificAttribute(stepTag, testerFlavorAttribute);

        if (prodTag.equals(stepTag.getTagName())) {
            readGlobalServiceId(stepTag).ifPresent(id -> prodAttributes.put(globalServiceIdAttribute, id));
        } else {
            if (readGlobalServiceId(stepTag).isPresent()) illegal("Attribute '" + globalServiceIdAttribute + "' is only valid on 'prod' tag");
        }

        switch (stepTag.getTagName()) {
            case testTag:
                if (Stream.iterate(stepTag, Objects::nonNull, Node::getParentNode)
                          .anyMatch(node -> prodTag.equals(node.getNodeName()))) {
                    // A production test
                    return List.of(new DeclaredTest(RegionName.from(XML.getValue(stepTag).trim())));
                }
                return List.of(new DeclaredZone(Environment.from(stepTag.getTagName()), Optional.empty(), false, athenzService, testerFlavor, readCloudAccount(stepTag)));
            case devTag, perfTag, stagingTag:
                return List.of(new DeclaredZone(Environment.from(stepTag.getTagName()), Optional.empty(), false, athenzService, testerFlavor, readCloudAccount(stepTag)));
            case prodTag: // regions, delay and parallel may be nested within, but we can flatten them
                return XML.getChildren(stepTag).stream()
                                               .flatMap(child -> readNonInstanceSteps(child, prodAttributes, stepTag).stream())
                                               .collect(Collectors.toList());
            case delayTag:
                return List.of(new Delay(Duration.ofSeconds(longAttribute("hours", stepTag) * 60 * 60 +
                                                            longAttribute("minutes", stepTag) * 60 +
                                                            longAttribute("seconds", stepTag))));
            case parallelTag: // regions and instances may be nested within
                return List.of(new ParallelSteps(XML.getChildren(stepTag).stream()
                                                    .flatMap(child -> readSteps(child, prodAttributes, parentTag).stream())
                                                    .collect(Collectors.toList())));
            case stepsTag: // regions and instances may be nested within
                return List.of(new Steps(XML.getChildren(stepTag).stream()
                                            .flatMap(child -> readSteps(child, prodAttributes, parentTag).stream())
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
                illegal("Exactly one of 'role' and 'address' must be present in 'email' elements.");

            addressAttribute.ifPresent(address -> emailAddresses.get(when).add(address));
            roleAttribute.ifPresent(role -> emailRoles.get(when).add(role));
        }
        return Notifications.of(emailAddresses, emailRoles);
    }

    private List<Endpoint> readEndpoints(Element parent, Optional<String> instance, List<Step> steps) {
        var endpointsElement = XML.getChild(parent, endpointsTag);
        if (endpointsElement == null) return List.of();

        Endpoint.Level level = instance.isEmpty() ? Endpoint.Level.application : Endpoint.Level.instance;
        Map<String, Endpoint> endpoints = new LinkedHashMap<>();
        for (var endpointElement : XML.getChildren(endpointsElement, endpointTag)) {
            String endpointId = stringAttribute("id", endpointElement).orElse(Endpoint.DEFAULT_ID);
            String containerId = requireStringAttribute("container-id", endpointElement);
            String msgPrefix = (level == Endpoint.Level.application ? "Application-level" : "Instance-level") +
                               " endpoint '" + endpointId + "': ";
            String invalidChild = level == Endpoint.Level.application ? "region" : "instance";
            if (!XML.getChildren(endpointElement, invalidChild).isEmpty()) illegal(msgPrefix + "invalid element '" + invalidChild + "'");

            List<Endpoint.Target> targets = new ArrayList<>();
            if (level == Endpoint.Level.application) {
                Optional<String> endpointRegion = stringAttribute("region", endpointElement);
                int weightSum = 0;
                for (var instanceElement : XML.getChildren(endpointElement, "instance")) {
                    String instanceName = instanceElement.getTextContent();
                    if (instanceName == null || instanceName.isBlank()) illegal(msgPrefix + "empty 'instance' element");
                    Optional<String> instanceRegion = stringAttribute("region", instanceElement);
                    if (endpointRegion.isPresent() == instanceRegion.isPresent())
                        illegal(msgPrefix + "'region' attribute must be declared on either <endpoint> or <instance> tag");
                    String weightFromAttribute = requireStringAttribute("weight", instanceElement);
                    int weight;
                    try {
                        weight = Integer.parseInt(weightFromAttribute);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(msgPrefix + "invalid weight value '" + weightFromAttribute + "'");
                    }
                    weightSum += weight;
                    targets.add(new Endpoint.Target(RegionName.from(endpointRegion.orElseGet(instanceRegion::get)),
                                                    InstanceName.from(instanceName),
                                                    weight));
                }
                if (weightSum == 0) illegal(msgPrefix + "sum of all weights must be positive, got " + weightSum);
            } else {
                if (stringAttribute("region", endpointElement).isPresent()) illegal(msgPrefix + "invalid 'region' attribute");
                for (var regionElement : XML.getChildren(endpointElement, "region")) {
                    String region = regionElement.getTextContent();
                    if (region == null || region.isBlank()) illegal(msgPrefix + "empty 'region' element");
                    targets.add(new Endpoint.Target(RegionName.from(region),
                                                    InstanceName.from(instance.get()),
                                                    1));
                }
            }
            if (targets.isEmpty() && level == Endpoint.Level.instance) {
                // No explicit targets given for instance level endpoint. Include all declared regions by default
                InstanceName instanceName = instance.map(InstanceName::from).get();
                steps.stream()
                     .filter(step -> step.concerns(Environment.prod))
                     .flatMap(step -> step.zones().stream())
                     .flatMap(zone -> zone.region().stream())
                     .distinct()
                     .map(region -> new Endpoint.Target(region, instanceName, 1))
                     .forEach(targets::add);
            }

            Endpoint endpoint = new Endpoint(endpointId, containerId, level, targets);
            if (endpoints.containsKey(endpoint.endpointId())) {
                illegal("Endpoint ID '" + endpoint.endpointId() + "' is specified multiple times");
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
                if (containsAfter(i, testTag, tags)) illegal(constraint);
                if (containsAfter(i, stagingTag, tags)) illegal(constraint);
                if (containsBefore(i, prodTag, tags)) illegal(constraint);
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
        if (value.isEmpty()) return 0;
        try {
            return Long.parseLong(value);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected an integer for attribute '" + attributeName +
                                               "' but got '" + value + "'");
        }
    }

    /**
     * Returns the given attribute as an integer, or {@code empty()} if it is not present
     */
    private Optional<Integer> optionalIntegerAttribute(String attributeName, Element tag) {
        String value = tag.getAttribute(attributeName);
        if (value.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(value));
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected an integer for attribute '" + attributeName +
                                               "' but got '" + value + "'");
        }
    }

    /** Returns the given non-blank attribute of tag as a string, if any */
    private static Optional<String> stringAttribute(String attributeName, Element tag) {
        String value = tag.getAttribute(attributeName);
        return Optional.of(value).filter(s -> !s.isBlank());
    }

    /** Returns the given non-blank attribute of tag or throw */
    private static String requireStringAttribute(String attributeName, Element tag) {
        return stringAttribute(attributeName, tag)
                .orElseThrow(() -> new IllegalArgumentException("Missing required attribute '" + attributeName +
                                                                "' in '" + tag.getTagName() + "'"));
    }

    private DeclaredZone readDeclaredZone(Environment environment, Optional<AthenzService> athenzService,
                                          Optional<String> testerFlavor, Element regionTag) {
        return new DeclaredZone(environment, Optional.of(RegionName.from(XML.getValue(regionTag).trim())),
                                readActive(regionTag), athenzService, testerFlavor,
                                readCloudAccount(regionTag));
    }

    private Optional<CloudAccount> readCloudAccount(Element tag) {
        return mostSpecificAttribute(tag, cloudAccountAttribute).map(CloudAccount::from);
    }

    private Optional<String> readGlobalServiceId(Element environmentTag) {
        String globalServiceId = environmentTag.getAttribute(globalServiceIdAttribute);
        if (globalServiceId.isEmpty()) return Optional.empty();
        deprecate(environmentTag, List.of(globalServiceIdAttribute), 7, "See https://cloud.vespa.ai/en/reference/routing#deprecated-syntax");
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
        String dateStart = tag.getAttribute("from-date");
        String dateEnd = tag.getAttribute("to-date");

        return new DeploymentSpec.ChangeBlocker(blockRevisions, blockVersions,
                                                TimeWindow.from(daySpec, hourSpec, zoneSpec, dateStart, dateEnd));
    }

    /** Returns true if the given value is "true", or if it is missing */
    private boolean trueOrMissing(String value) {
        return value == null || value.isEmpty() || value.equals("true");
    }

    private <T> T getWithFallback(Element parent, Element fallbackParent, String tagName, String attributeName,
                                  Function<String, T> mapper, T fallbackValue) {
        Element element = XML.getChild(parent, tagName);
        if (element == null) element = XML.getChild(fallbackParent, tagName);
        if (element == null) return fallbackValue;
        String attribute = element.getAttribute(attributeName);
        return attribute.isBlank() ? fallbackValue : mapper.apply(attribute);
    }

    private DeploymentSpec.UpgradePolicy readUpgradePolicy(String policy) {
        return switch (policy) {
            case "canary" -> UpgradePolicy.canary;
            case "default" -> UpgradePolicy.defaultPolicy;
            case "conservative" -> UpgradePolicy.conservative;
            default -> throw new IllegalArgumentException("Illegal upgrade policy '" + policy + "': " +
                                                          "Must be one of 'canary', 'default', 'conservative'");
        };
    }

    private DeploymentSpec.RevisionChange readRevisionChange(String revision) {
        return switch (revision) {
            case "when-clear" -> RevisionChange.whenClear;
            case "when-failing" -> RevisionChange.whenFailing;
            case "always" -> RevisionChange.always;
            default -> throw new IllegalArgumentException("Illegal upgrade revision change policy '" + revision + "': " +
                                                          "Must be one of 'always', 'when-failing', 'when-clear'");
        };
    }

    private DeploymentSpec.RevisionTarget readRevisionTarget(String revision) {
        return switch (revision) {
            case "next" -> RevisionTarget.next;
            case "latest" -> RevisionTarget.latest;
            default -> throw new IllegalArgumentException("Illegal upgrade revision target '" + revision + "': " +
                                                          "Must be one of 'next', 'latest'");
        };
    }

    private DeploymentSpec.UpgradeRollout readUpgradeRollout(String rollout) {
        return switch (rollout) {
            case "separate" -> UpgradeRollout.separate;
            case "leading" -> UpgradeRollout.leading;
            case "simultaneous" -> UpgradeRollout.simultaneous;
            default -> throw new IllegalArgumentException("Illegal upgrade rollout '" + rollout + "': " +
                                                          "Must be one of 'separate', 'leading', 'simultaneous'");
        };
    }

    private boolean readActive(Element regionTag) {
        String activeValue = regionTag.getAttribute("active");
        if ("".equals(activeValue)) return true; // Default to active
        deprecate(regionTag, List.of("active"), 7, "See https://cloud.vespa.ai/en/reference/routing#deprecated-syntax");
        if ("true".equals(activeValue)) return true;
        if ("false".equals(activeValue)) return false;
        throw new IllegalArgumentException("Value of 'active' attribute in region tag must be 'true' or 'false' " +
                                           "to control whether this region should receive traffic from the global endpoint of this application");
    }

    private void deprecate(Element element, List<String> attributes, int majorVersion, String message) {
        deprecatedElements.add(new DeprecatedElement(majorVersion, element.getTagName(), attributes, message));
    }

    private static boolean isEmptySpec(Element root) {
        if ( ! XML.getChildren(root).isEmpty()) return false;
        return    root.getAttributes().getLength() == 0
               || root.getAttributes().getLength() == 1 && root.hasAttribute("version");
    }

    /** Returns the given attribute from the given tag or its closest ancestor with the attribute. */
    private static Optional<String> mostSpecificAttribute(Element tag, String attributeName) {
        return Stream.iterate(tag, Objects::nonNull, Node::getParentNode)
                     .filter(Element.class::isInstance)
                     .map(Element.class::cast)
                     .flatMap(element -> stringAttribute(attributeName, element).stream())
                     .findFirst();
    }

    private static void illegal(String message) {
        throw new IllegalArgumentException(message);
    }

}
