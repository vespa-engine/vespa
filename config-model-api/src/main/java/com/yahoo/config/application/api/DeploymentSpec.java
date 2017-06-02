// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.io.IOUtils;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Specifies the environments and regions to which an application should be deployed.
 * This may be used both for inspection as part of an application model and to answer
 * queries about deployment from the command line. A main method is included for the latter usage.
 * 
 * A deployment consists of a number of steps executed in the order listed in deployment xml, as
 * well as some additional settings.
 * 
 * This is immutable.
 *
 * @author bratseth
 */
public class DeploymentSpec {

    /** The empty deployment spec, specifying no zones or rotation, and defaults for all settings */
    public static final DeploymentSpec empty = new DeploymentSpec(Optional.empty(), 
                                                                  UpgradePolicy.defaultPolicy, 
                                                                  ImmutableList.of(),
                                                                  "<deployment version='1.0'/>");
    
    private final Optional<String> globalServiceId;
    private final UpgradePolicy upgradePolicy;
    private final List<Step> steps;
    private final String xmlForm;

    public DeploymentSpec(Optional<String> globalServiceId, UpgradePolicy upgradePolicy, List<Step> steps) {
        this(globalServiceId, upgradePolicy, steps, null);
    }

    private DeploymentSpec(Optional<String> globalServiceId, UpgradePolicy upgradePolicy, 
                           List<Step> steps, String xmlForm) {
        validateTotalDelay(steps);
        this.globalServiceId = globalServiceId;
        this.upgradePolicy = upgradePolicy;
        this.steps = ImmutableList.copyOf(completeSteps(new ArrayList<>(steps)));
        this.xmlForm = xmlForm;
    }
    
    /** Throw an IllegalArgumentException if the total delay exceeds 24 hours */
    private static void validateTotalDelay(List<Step> steps) {
        long totalDelaySeconds = steps.stream().filter(step -> step instanceof Delay)
                                               .mapToLong(delay -> ((Delay)delay).duration().getSeconds())
                                               .sum();
        if (totalDelaySeconds > Duration.ofHours(24).getSeconds())
            throw new IllegalArgumentException("The total delay specified is " + Duration.ofSeconds(totalDelaySeconds) +
                                               " but max 24 hours is allowed");
    }
    
    /** Adds missing required steps and reorders steps to a permissible order */
    private static List<Step> completeSteps(List<Step> steps) {
        // Ensure no duplicate deployments to the same zone
        steps = new ArrayList<>(new LinkedHashSet<>(steps));
        
        // Add staging if required and missing
        if (steps.stream().anyMatch(step -> step.deploysTo(Environment.prod)) &&
            steps.stream().noneMatch(step -> step.deploysTo(Environment.staging))) {
            steps.add(new DeclaredZone(Environment.staging));
        }
        
        // Add test if required and missing
        if (steps.stream().anyMatch(step -> step.deploysTo(Environment.staging)) &&
            steps.stream().noneMatch(step -> step.deploysTo(Environment.test))) {
            steps.add(new DeclaredZone(Environment.test));
        }
        
        // Enforce order test, staging, prod
        DeclaredZone testStep = remove(Environment.test, steps);
        if (testStep != null)
            steps.add(0, testStep);
        DeclaredZone stagingStep = remove(Environment.staging, steps);
        if (stagingStep != null)
            steps.add(1, stagingStep);
        
        return steps;
    }

    /** 
     * Removes the first occurrence of a deployment step to the given environment and returns it.
     * 
     * @param environment
     * @return the removed step, or null if it is not present
     */
    private static DeclaredZone remove(Environment environment, List<Step> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).deploysTo(environment))
                return (DeclaredZone)steps.remove(i);
        }
        return null;
    }

    /** Returns the ID of the service to expose through global routing, if present */
    public Optional<String> globalServiceId() {
        return globalServiceId;
    }

    /** Returns the upgrade policy of this, which is defaultPolicy if none is specified */
    public UpgradePolicy upgradePolicy() { return upgradePolicy; }

    /** Returns the deployment steps of this in the order they will be performed */
    public List<Step> steps() { return steps; }

    /** Returns only the DeclaredZone deployment steps of this in the order they will be performed */
    public List<DeclaredZone> zones() { 
        return steps.stream().filter(step -> step instanceof DeclaredZone).map(DeclaredZone.class::cast)
                             .collect(Collectors.toList());
    }

    /** Returns the XML form of this spec, or null if it was not created by fromXml or is the empty spec */
    public String xmlForm() { return xmlForm; }

    /** Returns whether this deployment spec specifies the given zone, either implicitly or explicitly */
    public boolean includes(Environment environment, Optional<RegionName> region) {
        for (Step step : steps)
            if (step.deploysTo(environment, region)) return true;
        return false;
    }

    /**
     * Creates a deployment spec from XML.
     *
     * @throws IllegalArgumentException if the XML is invalid
     */
    public static DeploymentSpec fromXml(Reader reader) {
        try {
            return fromXml(IOUtils.readAll(reader));
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not read deployment spec", e);
        }
    }

    /**
     * Creates a deployment spec from XML.
     *
     * @throws IllegalArgumentException if the XML is invalid
     */
    public static DeploymentSpec fromXml(String xmlForm) {
        List<Step> steps = new ArrayList<>();
        Optional<String> globalServiceId = Optional.empty();
        Element root = XML.getDocument(xmlForm).getDocumentElement();
        for (Element environmentTag : XML.getChildren(root)) {
            if ( ! isEnvironmentName(environmentTag.getTagName())) continue;

            Environment environment = Environment.from(environmentTag.getTagName());

            if (environment == Environment.prod) {
                for (Element stepTag : XML.getChildren(environmentTag)) {
                    if (stepTag.getTagName().equals("delay"))
                        steps.add(new Delay(Duration.ofSeconds(longAttribute("hours",   stepTag) * 60 * 60 +
                                                               longAttribute("minutes", stepTag) * 60 +
                                                               longAttribute("seconds", stepTag))));
                    else // a region: deploy step
                        steps.add(new DeclaredZone(environment,
                                                   Optional.of(RegionName.from(XML.getValue(stepTag).trim())),
                                                   readActive(stepTag)));
                }
            }
            else {
                steps.add(new DeclaredZone(environment));
            }

            if (environment == Environment.prod)
                globalServiceId = readGlobalServiceId(environmentTag);
            else if (readGlobalServiceId(environmentTag).isPresent())
                throw new IllegalArgumentException("Attribute 'global-service-id' is only valid on 'prod' tag.");
        }
        return new DeploymentSpec(globalServiceId, readUpgradePolicy(root), steps, xmlForm);
    }
    
    /** Returns the given attribute as an integer, or 0 if it is not present */
    private static long longAttribute(String attributeName, Element tag) {
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

    private static boolean isEnvironmentName(String tagName) {
        return tagName.equals("test") || tagName.equals("staging") || tagName.equals("prod");
    }

    private static Optional<String> readGlobalServiceId(Element environmentTag) {
        String globalServiceId = environmentTag.getAttribute("global-service-id");
        if (globalServiceId == null || globalServiceId.isEmpty()) {
            return Optional.empty();
        }
        else {
            return Optional.of(globalServiceId);
        }
    }
    
    private static UpgradePolicy readUpgradePolicy(Element root) {
        Element upgradeElement = XML.getChild(root, "upgrade");
        if (upgradeElement == null) return UpgradePolicy.defaultPolicy;

        String policy = upgradeElement.getAttribute("policy");
        switch (policy) {
            case "canary" : return UpgradePolicy.canary;
            case "default" : return UpgradePolicy.defaultPolicy;
            case "conservative" : return UpgradePolicy.conservative;
            default : throw new IllegalArgumentException("Illegal upgrade policy '" + policy + "': " +
                                                         "Must be one of " + Arrays.toString(UpgradePolicy.values()));
        }
    }

    private static boolean readActive(Element regionTag) {
        String activeValue = regionTag.getAttribute("active");
        if ("true".equals(activeValue)) return true;
        if ("false".equals(activeValue)) return false;
        throw new IllegalArgumentException("Region tags must have an 'active' attribute set to 'true' or 'false' " +
                                           "to control whether the region should receive production traffic");
    }

    public static String toMessageString(Throwable t) {
        StringBuilder b = new StringBuilder();
        String lastMessage = null;
        String message;
        for (; t != null; t = t.getCause()) {
            message = t.getMessage();
            if (message == null) continue;
            if (message.equals(lastMessage)) continue;
            if (b.length() > 0) {
                b.append(": ");
            }
            b.append(message);
            lastMessage = message;
        }
        return b.toString();
    }

    /** This may be invoked by a continuous build */
    public static void main(String[] args) {
        if (args.length != 2 && args.length != 3) {
            System.err.println("Usage: DeploymentSpec [file] [environment] [region]?" +
                               "Returns 0 if the specified zone matches the deployment spec, 1 otherwise");
            System.exit(1);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(args[0]))) {
            DeploymentSpec spec = DeploymentSpec.fromXml(reader);
            Environment environment = Environment.from(args[1]);
            Optional<RegionName> region = args.length == 3 ? Optional.of(RegionName.from(args[2])) : Optional.empty();
            if (spec.includes(environment, region))
                System.exit(0);
            else
                System.exit(1);
        }
        catch (Exception e) {
            System.err.println("Exception checking deployment spec: " + toMessageString(e));
            System.exit(1);
        }
    }

    /** A delpoyment step */
    public abstract static class Step {
        
        /** Returns whether this step deploys to the given region */
        public final boolean deploysTo(Environment environment) {
            return deploysTo(environment, Optional.empty());
        }

        /** Returns whether this step deploys to the given environment, and (if specified) region */
        public abstract boolean deploysTo(Environment environment, Optional<RegionName> region);

    }

    /** A deployment step which is to wait for some time before progressing to the next step */
    public static class Delay extends Step {
        
        private final Duration duration;
        
        public Delay(Duration duration) {
            this.duration = duration;
        }
        
        public Duration duration() { return duration; }

        @Override
        public boolean deploysTo(Environment environment, Optional<RegionName> region) { return false; }

    }

    /** A deployment step which is to run deployment in a particular zone */
    public static class DeclaredZone extends Step {

        private final Environment environment;

        private Optional<RegionName> region;

        private final boolean active;

        public DeclaredZone(Environment environment) {
            this(environment, Optional.empty(), false);
        }

        public DeclaredZone(Environment environment, Optional<RegionName> region, boolean active) {
            if (environment != Environment.prod && region.isPresent())
                throw new IllegalArgumentException("Non-prod environments cannot specify a region");
            if (environment == Environment.prod && ! region.isPresent())
                throw new IllegalArgumentException("Prod environments must be specified with a region");
            this.environment = environment;
            this.region = region;
            this.active = active;
        }

        public Environment environment() { return environment; }

        /** The region name, or empty if not declared */
        public Optional<RegionName> region() { return region; }

        /** Returns whether this zone should receive production traffic */
        public boolean active() { return active; }

        @Override
        public boolean deploysTo(Environment environment, Optional<RegionName> region) {
            if (environment != this.environment) return false;
            if (region.isPresent() && ! region.equals(this.region)) return false;
            return true;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(environment, region);
        }
        
        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( !  (o instanceof DeclaredZone)) return false;
            DeclaredZone other = (DeclaredZone)o;
            if (this.environment != other.environment) return false;
            if ( ! this.region.equals(other.region())) return false;
            return true;
        }

    }

    /** Controls when this application will be upgraded to new Vespa versions */
    public enum UpgradePolicy {
        /** Canary: Applications with this policy will upgrade before any other */
        canary,
        /** Default: Will upgrade after all canary applications upgraded successfully. The default. */
        defaultPolicy,
        /** Will upgrade after most default applications upgraded successfully */
        conservative
    }

}
