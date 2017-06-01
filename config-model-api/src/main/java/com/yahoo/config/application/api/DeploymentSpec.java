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
import java.util.List;
import java.util.Optional;

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
    private final List<ZoneDeployment> zones;
    private final String xmlForm;

    public DeploymentSpec(Optional<String> globalServiceId, UpgradePolicy upgradePolicy, List<ZoneDeployment> zones) {
        this(globalServiceId, upgradePolicy, zones, null);
    }

    private DeploymentSpec(Optional<String> globalServiceId, UpgradePolicy upgradePolicy,
                           List<ZoneDeployment> zones, String xmlForm) {
        this.globalServiceId = globalServiceId;
        this.upgradePolicy = upgradePolicy;
        this.zones = ImmutableList.copyOf(completeSteps(new ArrayList<>(zones)));
        this.xmlForm = xmlForm;
    }
    
    /** Adds missing required steps and reorders steps to a permissible order */
    private static List<ZoneDeployment> completeSteps(List<ZoneDeployment> steps) {
        // Add staging if required and missing
        if (steps.stream().anyMatch(step -> step.environment() == Environment.prod) &&
            steps.stream().noneMatch(step -> step.environment() == Environment.staging))
            steps.add(new ZoneDeployment(Environment.staging));
        
        // Add test if required and missing
        if (steps.stream().anyMatch(step -> step.environment() == Environment.staging) &&
            steps.stream().noneMatch(step -> step.environment() == Environment.test))
        steps.add(new ZoneDeployment(Environment.test));
        
        // Enforce order test, staging, prod
        ZoneDeployment testStep = remove(Environment.test, steps);
        if (testStep != null)
            steps.add(0, testStep);
        ZoneDeployment stagingStep = remove(Environment.staging, steps);
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
    private static ZoneDeployment remove(Environment environment, List<ZoneDeployment> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).environment() == environment)
                return steps.remove(i);
        }
        return null;
    }

    /** Returns the ID of the service to expose through global routing, if present */
    public Optional<String> globalServiceId() {
        return globalServiceId;
    }

    /** Returns the upgrade policy of this, which is defaultPolicy if none is specified */
    public UpgradePolicy upgradePolicy() { return upgradePolicy; }

    /** Returns the zones this declares as a read-only list. */
    public List<ZoneDeployment> zones() { return zones; }
    
    /** Returns the XML form of this spec, or null if it was not created by fromXml or is the empty spec */
    public String xmlForm() { return xmlForm; }

    /** Returns whether this deployment spec specifies the given zone, either implicitly or explicitly */
    public boolean includes(Environment environment, Optional<RegionName> region) {
        for (ZoneDeployment zoneDeployment : zones)
            if (zoneDeployment.matches(environment, region)) return true;
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
        List<ZoneDeployment> zones = new ArrayList<>();
        Optional<String> globalServiceId = Optional.empty();
        Element root = XML.getDocument(xmlForm).getDocumentElement();
        for (Element environmentTag : XML.getChildren(root)) {
            if (!isEnvironmentName(environmentTag.getTagName())) continue;
            Environment environment = Environment.from(environmentTag.getTagName());
            List<Element> regionTags = XML.getChildren(environmentTag, "region");
            if (regionTags.isEmpty()) {
                zones.add(new ZoneDeployment(environment, Optional.empty(), false));
            } else {
                for (Element regionTag : regionTags) {
                    RegionName region = RegionName.from(XML.getValue(regionTag).trim());
                    boolean active = environment == Environment.prod && readActive(regionTag);
                    zones.add(new ZoneDeployment(environment, Optional.of(region), active));
                }
            }

            if (Environment.prod.equals(environment)) {
                globalServiceId = readGlobalServiceId(environmentTag);
            } else if (readGlobalServiceId(environmentTag).isPresent()) {
                throw new IllegalArgumentException("Attribute 'global-service-id' is only valid on 'prod' tag.");
            }
        }
        return new DeploymentSpec(globalServiceId, readUpgradePolicy(root), zones, xmlForm);
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
        
    }

    /** A deployment step which is to wait for some time before progressing to the next step */
    public static class Delay extends Step {
        
        private final Duration duration;
        
        public Delay(Duration duration) {
            this.duration = duration;
        }
        
        public Duration duration() { return duration; }
        
    }

    /** A deployment step which is to run deployment in a particular zone */
    public static class ZoneDeployment extends Step {

        private final Environment environment;

        private Optional<RegionName> region;

        private final boolean active;

        public ZoneDeployment(Environment environment) {
            this(environment, Optional.empty(), true);
        }

        public ZoneDeployment(Environment environment, Optional<RegionName> region, boolean active) {
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

        public boolean matches(Environment environment, Optional<RegionName> region) {
            return environment.equals(this.environment) && region.equals(this.region);
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
