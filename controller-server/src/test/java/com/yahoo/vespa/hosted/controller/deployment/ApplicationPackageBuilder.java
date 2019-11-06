// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.OptionalInt;
import java.util.StringJoiner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A builder that builds application packages for testing purposes.
 *
 * @author mpolden
 */
public class ApplicationPackageBuilder {

    private final StringBuilder environmentBody = new StringBuilder();
    private final StringBuilder validationOverridesBody = new StringBuilder();
    private final StringBuilder blockChange = new StringBuilder();
    private final StringJoiner notifications = new StringJoiner("/>\n  <email ",
                                                                "<notifications>\n  <email ",
                                                                "/>\n</notifications>\n").setEmptyValue("");
    private final StringBuilder endpointsBody = new StringBuilder();
    private final List<X509Certificate> trustedCertificates = new ArrayList<>();

    private OptionalInt majorVersion = OptionalInt.empty();
    private String instances = "default";
    private String upgradePolicy = null;
    private boolean explicitSystemTest = false;
    private boolean explicitStagingTest = false;
    private Environment environment = Environment.prod;
    private String globalServiceId = null;
    private String athenzIdentityAttributes = null;
    private String searchDefinition = "search test { }";

    public ApplicationPackageBuilder majorVersion(int majorVersion) {
        this.majorVersion = OptionalInt.of(majorVersion);
        return this;
    }

    public ApplicationPackageBuilder instances(String instances) {
        this.instances = instances;
        return this;
    }

    public ApplicationPackageBuilder systemTest() {
        this.explicitSystemTest = true;
        return this;
    }

    public ApplicationPackageBuilder stagingTest() {
        this.explicitStagingTest = true;
        return this;
    }

    public ApplicationPackageBuilder upgradePolicy(String upgradePolicy) {
        this.upgradePolicy = upgradePolicy;
        return this;
    }

    public ApplicationPackageBuilder environment(Environment environment) {
        this.environment = environment;
        return this;
    }

    public ApplicationPackageBuilder globalServiceId(String globalServiceId) {
        this.globalServiceId = globalServiceId;
        return this;
    }

    public ApplicationPackageBuilder endpoint(String endpointId, String containerId, String... regions) {
        endpointsBody.append("      <endpoint");
        endpointsBody.append(" id='").append(endpointId).append("'");
        endpointsBody.append(" container-id='").append(containerId).append("'");
        endpointsBody.append(">\n");
        for (var region : regions) {
            endpointsBody.append("        <region>").append(region).append("</region>\n");
        }
        endpointsBody.append("      </endpoint>\n");
        return this;
    }

    public ApplicationPackageBuilder region(RegionName regionName) {
        return region(regionName.value());
    }

    public ApplicationPackageBuilder region(String regionName) {
        environmentBody.append("      <region active='true'>");
        environmentBody.append(regionName);
        environmentBody.append("</region>\n");
        return this;
    }

    public ApplicationPackageBuilder parallel(String... regionName) {
        environmentBody.append("    <parallel>\n");
        Arrays.stream(regionName).forEach(this::region);
        environmentBody.append("    </parallel>\n");
        return this;
    }

    public ApplicationPackageBuilder delay(Duration delay) {
        environmentBody.append("    <delay seconds='");
        environmentBody.append(delay.getSeconds());
        environmentBody.append("'/>\n");
        return this;
    }

    public ApplicationPackageBuilder blockChange(boolean revision, boolean version, String daySpec, String hourSpec,
                                                 String zoneSpec) {
        blockChange.append("    <block-change");
        blockChange.append(" revision='").append(revision).append("'");
        blockChange.append(" version='").append(version).append("'");
        blockChange.append(" days='").append(daySpec).append("'");
        blockChange.append(" hours='").append(hourSpec).append("'");
        blockChange.append(" time-zone='").append(zoneSpec).append("'");
        blockChange.append("/>\n");
        return this;
    }

    public ApplicationPackageBuilder allow(ValidationId validationId) {
        validationOverridesBody.append("  <allow until='");
        validationOverridesBody.append(asIso8601Date(Instant.now().plus(Duration.ofDays(28))));
        validationOverridesBody.append("'>");
        validationOverridesBody.append(validationId.value());
        validationOverridesBody.append("</allow>\n");
        return this;
    }

    public ApplicationPackageBuilder athenzIdentity(AthenzDomain domain, AthenzService service) {
        this.athenzIdentityAttributes = String.format("athenz-domain='%s' athenz-service='%s'", domain.value(),
                                                      service.value());
        return this;
    }

    public ApplicationPackageBuilder emailRole(String role) {
        this.notifications.add("role=\"" + role + "\"");
        return this;
    }

    public ApplicationPackageBuilder emailAddress(String address) {
        this.notifications.add("address=\"" + address + "\"");
        return this;
    }

    /** Sets the content of the search definition test.sd */
    public ApplicationPackageBuilder searchDefinition(String testSearchDefinition) {
        this.searchDefinition = testSearchDefinition;
        return this;
    }

    public ApplicationPackageBuilder trust(X509Certificate certificate) {
        this.trustedCertificates.add(certificate);
        return this;
    }

    private byte[] deploymentSpec() {
        StringBuilder xml = new StringBuilder();
        xml.append("<deployment version='1.0' ");
        majorVersion.ifPresent(v -> xml.append("major-version='").append(v).append("' "));
        if(athenzIdentityAttributes != null) {
            xml.append(athenzIdentityAttributes);
        }
        xml.append(">\n");
        xml.append("  <instance id='").append(instances).append("'>\n");
        if (upgradePolicy != null) {
            xml.append("    <upgrade policy='");
            xml.append(upgradePolicy);
            xml.append("'/>\n");
        }
        xml.append(notifications);
        xml.append(blockChange);
        if (explicitSystemTest)
            xml.append("    <test />\n");
        if (explicitStagingTest)
            xml.append("    <staging />\n");
        xml.append("    <");
        xml.append(environment.value());
        if (globalServiceId != null) {
            xml.append(" global-service-id='");
            xml.append(globalServiceId);
            xml.append("'");
        }
        xml.append(">\n");
        xml.append(environmentBody);
        xml.append("    </");
        xml.append(environment.value());
        xml.append(">\n");
        xml.append("    <endpoints>\n");
        xml.append(endpointsBody);
        xml.append("    </endpoints>\n");
        xml.append("  </instance>\n");
        xml.append("</deployment>\n");
        return xml.toString().getBytes(UTF_8);
    }
    
    private byte[] validationOverrides() {
        String xml = "<validation-overrides version='1.0'>\n" +
                validationOverridesBody +
                "</validation-overrides>\n";
        return xml.getBytes(UTF_8);
    }

    private byte[] searchDefinition() { 
        return searchDefinition.getBytes(UTF_8);
    }

    private byte[] buildMeta() {
        return "{\"compileVersion\":\"6.1\",\"buildTime\":1000}".getBytes(UTF_8);
    }

    public ApplicationPackage build() {
        return build(false);
    }

    public ApplicationPackage build(boolean useApplicationDir) {
        String dir = "";
        if (useApplicationDir) {
            dir = "application/";
        }
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip)) {
            out.putNextEntry(new ZipEntry(dir + "deployment.xml"));
            out.write(deploymentSpec());
            out.closeEntry();
            out.putNextEntry(new ZipEntry(dir + "validation-overrides.xml"));
            out.write(validationOverrides());
            out.closeEntry();
            out.putNextEntry(new ZipEntry(dir + "search-definitions/test.sd"));
            out.write(searchDefinition());
            out.closeEntry();
            out.putNextEntry(new ZipEntry(dir + "build-meta.json"));
            out.write(buildMeta());
            out.closeEntry();
            out.putNextEntry(new ZipEntry(dir + "security/clients.pem"));
            out.write(X509CertificateUtils.toPem(trustedCertificates).getBytes(UTF_8));
            out.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ApplicationPackage(zip.toByteArray());
    }

    private static String asIso8601Date(Instant instant) {
        return new SimpleDateFormat("yyyy-MM-dd").format(Date.from(instant));
    }

}
