// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api.xml;

import com.yahoo.config.application.api.Bcp;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.DeploymentSpec.DeclaredTest;
import com.yahoo.config.application.api.DeploymentSpec.DeclaredZone;
import com.yahoo.config.application.api.DeploymentSpec.Delay;
import com.yahoo.config.application.api.DeploymentSpec.DeprecatedElement;
import com.yahoo.config.application.api.DeploymentSpec.DevSpec;
import com.yahoo.config.application.api.DeploymentSpec.ParallelSteps;
import com.yahoo.config.application.api.DeploymentSpec.RevisionChange;
import com.yahoo.config.application.api.DeploymentSpec.RevisionTarget;
import com.yahoo.config.application.api.DeploymentSpec.Step;
import com.yahoo.config.application.api.DeploymentSpec.Steps;
import com.yahoo.config.application.api.DeploymentSpec.UpgradePolicy;
import com.yahoo.config.application.api.DeploymentSpec.UpgradeRollout;
import com.yahoo.config.application.api.Endpoint;
import com.yahoo.config.application.api.Endpoint.Level;
import com.yahoo.config.application.api.Endpoint.Target;
import com.yahoo.config.application.api.Notifications;
import com.yahoo.config.application.api.Notifications.Role;
import com.yahoo.config.application.api.Notifications.When;
import com.yahoo.config.application.api.TimeWindow;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.config.provision.ZoneEndpoint.AccessType;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;
import com.yahoo.config.provision.zone.ZoneId;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparingInt;

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
    private static final String testerTag = "tester";
    private static final String nodesTag = "nodes";
    private static final String majorVersionAttribute = "major-version";
    private static final String cloudAccountAttribute = "cloud-account";
    private static final String hostTTLAttribute = "empty-host-ttl";

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
        List<Endpoint> applicationEndpoints = new ArrayList<>();
        if ( ! containsTag(instanceTag, root)) { // deployment spec skipping explicit instance -> "default" instance
            steps.addAll(readInstanceContent("default", root, new HashMap<>(), root, Bcp.empty()));
        }
        else {
            if (XML.getChildren(root).stream().anyMatch(child -> child.getTagName().equals(prodTag)))
                illegal("A deployment spec cannot have both a <prod> tag and an " +
                        "<instance> tag under the root: " +
                        "Wrap the prod tags inside the appropriate instance");

            for (Element child : XML.getChildren(root)) {
                String tagName = child.getTagName();
                Bcp defaultBcp = readBcp(root, Optional.empty(), List.of(), List.of(), Map.of());
                if (tagName.equals(instanceTag)) {
                    steps.addAll(readInstanceContent(child.getAttribute(idAttribute), child, new HashMap<>(), root, defaultBcp));
                } else {
                    steps.addAll(readNonInstanceSteps(child, new HashMap<>(), root, defaultBcp)); // (No global service id here)
                }
            }
            readEndpoints(root, Optional.empty(), steps, applicationEndpoints, Map.of());
        }
        DevSpec devSpec = readDevSpec(root);

        return new DeploymentSpec(steps,
                                  optionalIntegerAttribute(majorVersionAttribute, root),
                                  stringAttribute(athenzDomainAttribute, root).map(AthenzDomain::from),
                                  stringAttribute(athenzServiceAttribute, root).map(AthenzService::from),
                                  readCloudAccounts(root),
                                  readHostTTL(root),
                                  applicationEndpoints,
                                  xmlForm,
                                  deprecatedElements,
                                  devSpec);
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
                                                             Element parentTag,
                                                             Bcp defaultBcp) {
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
        Map<CloudName, CloudAccount> cloudAccounts = readCloudAccounts(instanceElement);
        Optional<Duration> hostTTL = readHostTTL(instanceElement);
        Notifications notifications = readNotifications(instanceElement, parentTag);

        // Values where there is no default
        Tags tags = XML.attribute(tagsTag, instanceElement).map(Tags::fromString).orElse(Tags.empty());
        List<Step> steps = new ArrayList<>();
        for (Element instanceChild : XML.getChildren(instanceElement))
            steps.addAll(readNonInstanceSteps(instanceChild, prodAttributes, instanceChild, defaultBcp));
        List<Endpoint> endpoints = new ArrayList<>();
        Map<ClusterSpec.Id, Map<ZoneId, ZoneEndpoint>> zoneEndpoints = new LinkedHashMap<>();
        readEndpoints(instanceElement, Optional.of(instanceNameString), steps, endpoints, zoneEndpoints);
        Bcp bcp = complete(readBcp(instanceElement, Optional.of(instanceNameString), steps, endpoints, zoneEndpoints).orElse(defaultBcp), steps);
        validateEndpoints(endpoints);

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
                                                             athenzService,
                                                             cloudAccounts,
                                                             hostTTL,
                                                             notifications,
                                                             endpoints,
                                                             zoneEndpoints,
                                                             bcp,
                                                             now))
                     .toList();
    }

    private void validateEndpoints(List<Endpoint> endpoints) {
        Set<String> endpointIds = new HashSet<>();
        for (Endpoint endpoint : endpoints) {
            if ( ! endpointIds.add(endpoint.endpointId()))
                illegal("Endpoint id '" + endpoint.endpointId() + "' is specified multiple times");
        }
    }

    private List<Step> readSteps(Element stepTag, Map<String, String> prodAttributes, Element parentTag, Bcp defaultBcp) {
        if (stepTag.getTagName().equals(instanceTag))
            return new ArrayList<>(readInstanceContent(stepTag.getAttribute(idAttribute), stepTag, prodAttributes, parentTag, defaultBcp));
        else
            return readNonInstanceSteps(stepTag, prodAttributes, parentTag, defaultBcp);

    }

    // Consume the given tag as 0-N steps. 0 if it is not a step, >1 if it contains multiple nested steps that should be flattened
    @SuppressWarnings("fallthrough")
    private List<Step> readNonInstanceSteps(Element stepTag, Map<String, String> prodAttributes, Element parentTag, Bcp defaultBcp) {
        Optional<AthenzService> athenzService = mostSpecificAttribute(stepTag, athenzServiceAttribute).map(AthenzService::from);
        Optional<String> testerNodes = mostSpecificSibling(stepTag, testerTag).map(tester -> XML.getChild(tester, nodesTag)).map(XML::toString);

        switch (stepTag.getTagName()) {
            case testTag:
                if (Stream.iterate(stepTag, Objects::nonNull, Node::getParentNode)
                          .anyMatch(node -> prodTag.equals(node.getNodeName()))) {
                    return List.of(new DeclaredTest(RegionName.from(XML.getValue(stepTag).trim()), readHostTTL(stepTag))); // A production test
                }
            case stagingTag: // Intentional fallthrough from test tag.
                return List.of(new DeclaredZone(Environment.from(stepTag.getTagName()), Optional.empty(), athenzService, testerNodes, readCloudAccounts(stepTag), readHostTTL(stepTag)));
            case prodTag: // regions, delay and parallel may be nested within, but we can flatten them
                return XML.getChildren(stepTag).stream()
                                               .flatMap(child -> readNonInstanceSteps(child, prodAttributes, stepTag, defaultBcp).stream())
                                               .toList();
            case delayTag:
                return List.of(new Delay(Duration.ofSeconds(longAttribute("hours", stepTag) * 60 * 60 +
                                                            longAttribute("minutes", stepTag) * 60 +
                                                            longAttribute("seconds", stepTag))));
            case parallelTag: // regions and instances may be nested within
                return List.of(new ParallelSteps(XML.getChildren(stepTag).stream()
                                                    .flatMap(child -> readSteps(child, prodAttributes, parentTag, defaultBcp).stream())
                                                    .toList()));
            case stepsTag: // regions and instances may be nested within
                return List.of(new Steps(XML.getChildren(stepTag).stream()
                                            .flatMap(child -> readSteps(child, prodAttributes, parentTag, defaultBcp).stream())
                                            .toList()));
            case regionTag:
                return List.of(readDeclaredZone(Environment.prod, athenzService, testerNodes, stepTag));
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

    private void readEndpoints(Element parent, Optional<String> instance, List<Step> steps, List<Endpoint> endpoints,
                               Map<ClusterSpec.Id, Map<ZoneId, ZoneEndpoint>> zoneEndpoints) {
        var endpointsElement = XML.getChild(parent, endpointsTag);
        if (endpointsElement == null) return;

        Endpoint.Level level = instance.isEmpty() ? Endpoint.Level.application : Endpoint.Level.instance;
        Map<String, Map<RegionName, List<ZoneEndpoint>>> endpointsByZone = new LinkedHashMap<>();
        for (Element endpointElement : XML.getChildren(endpointsElement, endpointTag).stream() // Read zone settings first.
                .sorted(comparingInt(endpoint -> getZoneEndpointType(endpoint, level).isPresent() ? 0 : 1)).toList()) {
            Optional<Endpoint> endpoint = readEndpoint(parent, endpointElement, level, instance, steps, List.of(), endpointsByZone);
            endpoint.ifPresent(e -> endpoints.add(e));
        }
        validateAndConsolidate(endpointsByZone, zoneEndpoints);
    }

    /**
     * @param parentElement
     * @param endpointElement
     * @param level decide what this method is reading TODO: Split into different methods instead
     * @param instance the instance this applies to, or empty if it does not apply to an instance (application endpoints)
     * @param steps
     * @param forRegions the regions this applies to (for bcp), or empty (otherwise) to read this from "region" subelements
     * @param endpointsByZone a map containing any zone endpoints read by this
     * @return the endpoint read, unless it is added to endspointsByZone instead *sob*
     */
    static Optional<Endpoint> readEndpoint(Element parentElement,
                                           Element endpointElement,
                                           Endpoint.Level level,
                                           Optional<String> instance,
                                           List<Step> steps,
                                           Collection<RegionName> forRegions,
                                           Map<String, Map<RegionName, List<ZoneEndpoint>>> endpointsByZone) {
        String containerId = requireStringAttribute("container-id", endpointElement);
        Optional<String> endpointId = stringAttribute("id", endpointElement);
        Optional<String> zoneEndpointType = getZoneEndpointType(endpointElement, level);
        String msgPrefix = (level == Endpoint.Level.application ? "Application-level" : "Instance-level") + " endpoint" +
                           (zoneEndpointType.isEmpty() ? " '" + endpointId.orElse(Endpoint.DEFAULT_ID) + "': " : ": ");

        if (zoneEndpointType.isPresent() && endpointId.isPresent())
            illegal(msgPrefix + "cannot declare 'id' with type 'zone' or 'private'");

        String invalidChild = level == Endpoint.Level.application ? "region" : "instance";
        if ( ! XML.getChildren(endpointElement, invalidChild).isEmpty())
            illegal(msgPrefix + "invalid element '" + invalidChild + "'");

        boolean enabled = readEnabled(endpointElement, zoneEndpointType, msgPrefix);
        List<AllowedUrn> allowedUrns = readAllowedUrns(endpointElement, zoneEndpointType, containerId, msgPrefix);

        List<Endpoint.Target> targets = new ArrayList<>();
        if (level == Endpoint.Level.application) {
            if ( ! forRegions.isEmpty()) throw new IllegalStateException("Illegal combination");
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

            Set<RegionName> regions = new LinkedHashSet<>(forRegions);
            List<Element> regionElements = XML.getChildren(endpointElement, "region");
            if ( ! regions.isEmpty() && ! regionElements.isEmpty())
                illegal("Endpoints in <" + parentElement.getTagName() + "> cannot contain <region> children");
            for (var regionElement : XML.getChildren(endpointElement, "region")) {
                String region = regionElement.getTextContent();
                if (region == null || region.isBlank())
                    illegal(msgPrefix + "empty 'region' element");
                if (   zoneEndpointType.isEmpty()
                    && Stream.of(RegionName.from(region), null)
                             .map(endpointsByZone.getOrDefault(containerId, new HashMap<>())::get)
                             .flatMap(maybeEndpoints -> maybeEndpoints == null ? Stream.empty() : maybeEndpoints.stream())
                             .anyMatch(endpoint -> ! endpoint.isPublicEndpoint()))
                    illegal(msgPrefix + "targets zone endpoint in '" + region + "' with 'enabled' set to 'false'");
                if ( ! regions.add(RegionName.from(region)))
                    illegal(msgPrefix + "duplicate 'region' element: '" + region + "'");
            }

            if (zoneEndpointType.isPresent()) {
                if (regions.isEmpty()) regions.add(null);
                ZoneEndpoint endpoint = switch (zoneEndpointType.get()) {
                    case "zone" -> new ZoneEndpoint(enabled, false, List.of());
                    case "private" -> new ZoneEndpoint(true, true, allowedUrns); // Doesn't turn off public visibility.
                    default -> throw new IllegalArgumentException("unsupported zone endpoint type '" + zoneEndpointType.get() + "'");
                };
                for (RegionName region : regions) endpointsByZone.computeIfAbsent(containerId, __ -> new LinkedHashMap<>())
                                                                 .computeIfAbsent(region, __ -> new ArrayList<>())
                                                                 .add(endpoint);
            }
            else {
                if (regions.isEmpty()) {
                    // No explicit targets given for instance level endpoint. Include all declared, enabled regions by default
                    List<RegionName> declared =
                            steps.stream()
                                 .filter(step -> step.concerns(Environment.prod))
                                 .flatMap(step -> step.zones().stream())
                                 .flatMap(zone -> zone.region().stream())
                                 .toList();
                    if (declared.isEmpty()) illegal(msgPrefix + "no declared regions to target");

                    declared.stream().filter(region -> Stream.of(region, null)
                                                             .map(endpointsByZone.getOrDefault(containerId, new HashMap<>())::get)
                                                             .flatMap(maybeEndpoints -> maybeEndpoints == null ? Stream.empty() : maybeEndpoints.stream())
                                                             .allMatch(ZoneEndpoint::isPublicEndpoint))
                            .forEach(regions::add);
                }
                if (regions.isEmpty()) illegal(msgPrefix + "all eligible zone endpoints have 'enabled' set to 'false'");
                InstanceName instanceName = instance.map(InstanceName::from).get();
                for (RegionName region : regions) targets.add(new Target(region, instanceName, 1));
            }
        }

        if (zoneEndpointType.isEmpty())
            return Optional.of(new Endpoint(endpointId.orElse(Endpoint.DEFAULT_ID), containerId, level, targets));
        return Optional.empty();
    }

    static boolean readEnabled(Element endpointElement, Optional<String> zoneEndpointType, String msgPrefix) {
        return XML.attribute("enabled", endpointElement)
                  .map(value -> {
                      if (zoneEndpointType.isEmpty() || ! zoneEndpointType.get().equals("zone"))
                          illegal(msgPrefix + "only endpoints of type 'zone' can specify 'enabled'");

               return switch (value) {
                   case "true" -> true;
                   case "false" -> false;
                   default -> throw new IllegalArgumentException(msgPrefix + "invalid 'enabled' value; must be 'true' or 'false'");
               };
           }).orElse(true);
    }

    static List<AllowedUrn> readAllowedUrns(Element endpointElement, Optional<String> zoneEndpointType, String containerId, String msgPrefix) {
        List<AllowedUrn> allowedUrns = new ArrayList<>();
        for (var allow : XML.getChildren(endpointElement, "allow")) {
            if (zoneEndpointType.isEmpty() || ! zoneEndpointType.get().equals("private"))
                illegal(msgPrefix + "only endpoints of type 'private' can specify 'allow' children");

            switch (requireStringAttribute("with", allow)) {
                case "aws-private-link" -> allowedUrns.add(new AllowedUrn(AccessType.awsPrivateLink, requireStringAttribute("arn", allow)));
                case "gcp-service-connect" -> allowedUrns.add(new AllowedUrn(AccessType.gcpServiceConnect, requireStringAttribute("project", allow)));
                default -> illegal("Private endpoint for container-id '" + containerId + "': " +
                                   "invalid attribute 'with': '" + requireStringAttribute("with", allow) + "'");
            }
        }
        return allowedUrns;
    }

    static Bcp readBcp(Element parent, Optional<String> instance, List<Step> steps,
                       List<Endpoint> endpoints, Map<ClusterSpec.Id, Map<ZoneId, ZoneEndpoint>> zoneEndpoints) {
        Element bcpElement = XML.getChild(parent, "bcp");
        if (bcpElement == null) return Bcp.empty();
        Optional<Duration> defaultDeadline = XML.attribute("deadline", bcpElement).map(value -> toDuration(value, "deadline"));

        List<Bcp.Group> groups = new ArrayList<>();
        Map<String, Map<RegionName, List<ZoneEndpoint>>> endpointsByZone = new LinkedHashMap<>();
        for (Element groupElement : XML.getChildren(bcpElement, "group")) {
            List<Bcp.RegionMember> regions = new ArrayList<>();
            for (Element regionElement : XML.getChildren(groupElement, "region")) {
                double fraction = toDouble(XML.attribute("fraction", regionElement).orElse(null), "fraction").orElse(1.0);
                regions.add(new Bcp.RegionMember(RegionName.from(XML.getValue(regionElement)), fraction));
            }
            for (Element endpointElement : XML.getChildren(groupElement, "endpoint")) {
                if (instance.isEmpty()) illegal("The default <bcp> element at the root cannot define endpoints");
                Optional<Endpoint> endpoint = readEndpoint(groupElement,
                                                           endpointElement,
                                                           Endpoint.Level.instance,
                                                           instance,
                                                           steps,
                                                           regions.stream().map(r -> r.region()).toList(),
                                                           endpointsByZone);
                endpoint.ifPresent(e -> endpoints.add(e));
            }

            Duration deadline = XML.attribute("deadline", groupElement).map(value -> toDuration(value, "deadline"))
                                   .orElse(defaultDeadline.orElse(Duration.ZERO));
            groups.add(new Bcp.Group(regions, deadline));
        }
        validateAndConsolidate(endpointsByZone, zoneEndpoints);
        return new Bcp(groups, defaultDeadline);
    }

    /**
     * A bcp instance as written and imported may either specify groups containing all regions,
     * or no groups, meaning it should contain one group with all regions.
     * This adds that missing implicit group when appropriate.
     */
    private Bcp complete(Bcp bcp, List<Step> steps) {
        if ( ! bcp.groups().isEmpty()) return bcp; // has explicit groups
        var group = new Bcp.Group(prodRegions(steps).stream().map(region -> new Bcp.RegionMember(region, 1.0)).toList(),
                                  bcp.defaultDeadline().orElse(Duration.ZERO));
        return bcp.withGroups(List.of(group));
    }

    private Set<RegionName> prodRegions(List<Step> steps) {
        return steps.stream()
                    .flatMap(s -> s.zones().stream())
                    .filter(zone -> zone.environment().isProduction())
                    .flatMap(z -> z.region().stream())
                    .collect(Collectors.toSet());
    }

    static void validateAndConsolidate(Map<String, Map<RegionName, List<ZoneEndpoint>>> in, Map<ClusterSpec.Id, Map<ZoneId, ZoneEndpoint>> out) {
        in.forEach((cluster, regions) -> {
            List<ZoneEndpoint> wildcards = regions.remove(null);
            ZoneEndpoint wildcardZoneEndpoint = null;
            ZoneEndpoint wildcardPrivateEndpoint = null;
            if (wildcards != null) {
                for (ZoneEndpoint endpoint : wildcards) {
                    if (endpoint.isPrivateEndpoint()) {
                        if (wildcardPrivateEndpoint != null) illegal("Multiple private endpoints (for all regions) declared for " +
                                                                     "container id '" + cluster + "'");
                        wildcardPrivateEndpoint = endpoint;
                    }
                    else {
                        if (wildcardZoneEndpoint != null) illegal("Multiple zone endpoints (for all regions) declared " +
                                                                  "for container id '" + cluster + "'");
                        wildcardZoneEndpoint = endpoint;
                    }
                }
            }
            for (RegionName region : regions.keySet()) {
                ZoneEndpoint zoneEndpoint = null;
                ZoneEndpoint privateEndpoint = null;
                for (ZoneEndpoint endpoint : regions.getOrDefault(region, List.of())) {
                    if (endpoint.isPrivateEndpoint()) {
                        if (privateEndpoint != null) illegal("Multiple private endpoints declared for " +
                                                             "container id '" + cluster + "' in region '" + region + "'");
                        privateEndpoint = endpoint;
                    }
                    else {
                        if (zoneEndpoint != null) illegal("Multiple zone endpoints (without regions) declared " +
                                                          "for container id '" + cluster + "' in region '" + region + "'");
                        zoneEndpoint = endpoint;
                    }
                }
                if (wildcardZoneEndpoint != null && zoneEndpoint != null) illegal("Zone endpoint for container id '" + cluster + "' declared " +
                                                                                  "both with region '" + region + "', and for all regions.");
                if (wildcardPrivateEndpoint != null && privateEndpoint != null) illegal("Private endpoint for container id '" + cluster + "' declared " +
                                                                                        "both with region '" + region + "', and for all regions.");

                if (zoneEndpoint == null) zoneEndpoint = wildcardZoneEndpoint;
                if (privateEndpoint == null) privateEndpoint = wildcardPrivateEndpoint;

                // Gosh, we made it here! Now we'll combine the settings for zone and private types into one ZoneEndpoint to rule them all.
                out.computeIfAbsent(ClusterSpec.Id.from(cluster), __ -> new LinkedHashMap<>())
                   .put(ZoneId.from(Environment.prod, region), new ZoneEndpoint(zoneEndpoint == null || zoneEndpoint.isPublicEndpoint(),
                                                                                privateEndpoint != null,
                                                                                privateEndpoint != null ? privateEndpoint.allowedUrns() : List.of()));
            }
            out.computeIfAbsent(ClusterSpec.Id.from(cluster), __ -> new LinkedHashMap<>())
               .put(null, new ZoneEndpoint(wildcardZoneEndpoint == null || wildcardZoneEndpoint.isPublicEndpoint(),
                                           wildcardPrivateEndpoint != null,
                                           wildcardPrivateEndpoint != null ? wildcardPrivateEndpoint.allowedUrns() : List.of()));
        });
    }

    /** Returns endpoint type if a private or zone type endpoint, throws if invalid, or otherwise returns empty (global, application). */
    static Optional<String> getZoneEndpointType(Element endpoint, Level level) {
        Optional<String> type = XML.attribute("type", endpoint);
        if (type.isPresent() && ! List.of("zone", "private", "global", "application").contains(type.get()))
            illegal("Illegal endpoint type '" + type.get() + "'");

        String implied = switch (level) { case instance -> "global"; case application -> "application"; };
        if (type.isEmpty() || type.get().equals(implied)) return Optional.empty();
        if (level == Level.instance && (type.get().equals("zone") || type.get().equals("private"))) return type;
        throw new IllegalArgumentException("Endpoints at " + level + " level cannot be of type '" + type.get() + "'");
    }

    /**
     * Imposes some constraints on tag order which are not expressible in the schema
     */
    private void validateTagOrder(Element root) {
        List<String> tags = XML.getChildren(root).stream().map(Element::getTagName).toList();
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
        return stringAttribute(attributeName, tag, true);
    }

    /** Returns the given non-blank attribute of tag as a string, if any */
    private static Optional<String> stringAttribute(String attributeName, Element tag, boolean ignoreBlanks) {
        String value = tag.getAttribute(attributeName);
        return Optional.of(value).filter(s -> (tag.getAttributeNode(attributeName) != null && ! ignoreBlanks || ! s.isBlank()));
    }

    /** Returns the given non-blank attribute of tag or throw */
    private static String requireStringAttribute(String attributeName, Element tag) {
        return stringAttribute(attributeName, tag)
                .orElseThrow(() -> new IllegalArgumentException("Missing required attribute '" + attributeName +
                                                                "' in '" + tag.getTagName() + "'"));
    }

    private DeclaredZone readDeclaredZone(Environment environment, Optional<AthenzService> athenzService,
                                          Optional<String> testerNodes, Element regionTag) {
        return new DeclaredZone(environment, Optional.of(RegionName.from(XML.getValue(regionTag).trim())),
                                athenzService, testerNodes,
                                readCloudAccounts(regionTag), readHostTTL(regionTag));
    }

    private Map<CloudName, CloudAccount> readCloudAccounts(Element tag) {
        return mostSpecificAttribute(tag, cloudAccountAttribute, false)
                .map(value -> {
                    Map<CloudName, CloudAccount> accounts = new HashMap<>();
                    for (String part : value.split(",")) {
                        CloudAccount account = CloudAccount.from(part);
                        accounts.merge(account.cloudName(), account, (o, n) -> {
                            throw illegal("both '" + o.account() + "' and '" + n.account() + "' " +
                                          "are declared for cloud '" + o.cloudName() + "', in '" + value + "'");
                        });
                    }
                    return accounts;
                })
                .orElse(Map.of());
    }

    private Optional<Duration> readHostTTL(Element tag) {
        return mostSpecificAttribute(tag, hostTTLAttribute).map(s -> toDuration(s, "empty host TTL"));
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

    private DevSpec readDevSpec(Element root) {
        Element devElement = XML.getChild(root, devTag);
        if (devElement == null) return DevSpec.empty;

        Optional<AthenzService> athenzService = XML.attribute(athenzServiceAttribute, devElement).map(AthenzService::from);
        Map<CloudName, CloudAccount> cloudAccounts = readCloudAccounts(devElement);
        Optional<Duration> hostTTL = XML.attribute(hostTTLAttribute, devElement).map(s -> toDuration(s, "host TTL"));
        Tags tags = XML.attribute(tagsTag, devElement).map(Tags::fromString).orElse(Tags.empty());

        Map<ClusterSpec.Id, ZoneEndpoint> endpoints = new LinkedHashMap<>();
        Element endpointsElement = XML.getChild(devElement, endpointsTag);
        if (endpointsElement != null) {
            for (Element endpointElement : XML.getChildren(endpointsElement, endpointTag)) {
                readDevZoneEndpoint(endpointElement, endpoints);
            }
        }
        return new DevSpec(athenzService, Optional.of(cloudAccounts), hostTTL, tags, endpoints);
    }

    // TODO: if the other readEndpoints is ever refactored, factor in this, too.
    private static void readDevZoneEndpoint(Element endpointElement, Map<ClusterSpec.Id, ZoneEndpoint> endpoints) {
        String endpointType = getZoneEndpointType(endpointElement, Level.instance)
                .orElseThrow(() -> new IllegalArgumentException("Illegal endpoint type for <dev>: only 'zone' and 'private' endpoints are allowed"));

        if ( ! XML.getChildren(endpointElement, regionTag).isEmpty())
            throw new IllegalArgumentException("Illegal 'region' children of <endpoint> in <dev>");

        String containerId = requireStringAttribute("container-id", endpointElement);
        boolean enabled = readEnabled(endpointElement, Optional.of(endpointType), "Dev endpoint");
        List<AllowedUrn> allowedUrns = readAllowedUrns(endpointElement, Optional.of(endpointType), containerId, "Dev endpoint");

        ZoneEndpoint endpoint = switch (endpointType) {
            case "zone" -> new ZoneEndpoint(enabled, false, List.of());
            case "private" -> new ZoneEndpoint(true, true, allowedUrns); // Doesn't turn off public visibility.
            default -> throw new IllegalArgumentException("Unsupported zone endpoint type '" + endpointType + "'");
        };
        endpoints.merge(ClusterSpec.Id.from(containerId), endpoint, (o, n) -> {
            if (o.isPrivateEndpoint() == n.isPrivateEndpoint())
                throw new IllegalArgumentException("Multiple '" + (o.isPrivateEndpoint() ? "private" : "zone") +
                                                   "' type endpoints declared for container-id '" + containerId + "' in <dev>");

            return new ZoneEndpoint(o.isPublicEndpoint() && n.isPublicEndpoint(),   // Private one doesn't affect this.
                                    o.isPrivateEndpoint() || n.isPrivateEndpoint(), // One has to be private, since we merge.
                                    new ArrayList<>() {{ addAll(o.allowedUrns()); addAll(n.allowedUrns()); }});
        });
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
    private static Optional<String> mostSpecificAttribute(Element tag, String attributeName, boolean ignoreBlanks) {
        return Stream.iterate(tag, Objects::nonNull, Node::getParentNode)
                     .filter(Element.class::isInstance)
                     .map(Element.class::cast)
                     .flatMap(element -> stringAttribute(attributeName, element, ignoreBlanks).stream())
                     .findFirst();
    }

    /** Returns the given attribute from the given tag or its closest ancestor with the attribute. */
    private static Optional<String> mostSpecificAttribute(Element tag, String attributeName) {
        return mostSpecificAttribute(tag, attributeName, true);
    }

    /** Returns the first encountered sibling with the given name, or sibling of parent, or sibling of grandparent, etc.. */
    private static Optional<Element> mostSpecificSibling(Element tag, String siblingName) {
        return Stream.iterate(tag, Objects::nonNull, Node::getParentNode)
                     .filter(Element.class::isInstance)
                     .map(Element.class::cast)
                     .map(element -> XML.getChild(element, siblingName))
                     .filter(Objects::nonNull)
                     .findFirst();
    }

    /**
     * Returns a string consisting of a number followed by "m", "h" or "d" to a duration given in that unit,
     * or zero duration if null or blank.
     */
    private static Duration toDuration(String durationSpec, String sourceDescription) {
        try {
            if (durationSpec == null || durationSpec.isBlank()) return Duration.ZERO;
            durationSpec = durationSpec.trim().toLowerCase();
            var magnitude = toMagnitude(durationSpec);
            return switch (durationSpec.substring(durationSpec.length() - 1)) {
                case "m" -> Duration.ofMinutes(magnitude);
                case "h" -> Duration.ofHours(magnitude);
                case "d" -> Duration.ofDays(magnitude);
                default -> throw new IllegalArgumentException("Must end by 'm', 'h' or 'd'");
            };
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Illegal " + sourceDescription + " '" + durationSpec + "'", e);
        }
    }

    private static int toMagnitude(String durationSpec) {
        try {
            return Integer.parseInt(durationSpec.substring(0, durationSpec.length() - 1));
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Must be an integer followed by 'm', 'h' or 'd'");
        }
    }

    private static OptionalDouble toDouble(String value, String sourceDescription) {
        try {
            if (value == null || value.isBlank()) return OptionalDouble.empty();
            return OptionalDouble.of(Double.parseDouble(value));
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Illegal " + sourceDescription + " '" + value + "': " +
                                               "Must be a number between 0.0 and 1.0");
        }
    }

    private static IllegalArgumentException illegal(String message) {
        throw new IllegalArgumentException(message);
    }

}
