// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.container.jdisc.HttpRequest;

import org.junit.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class PrepareParamsTest {

    @Test
    public void testCorrectParsing() {
        PrepareParams prepareParams = createParams("http://foo:19071/application/v2/",
                TenantName.defaultName());

        assertThat(prepareParams.getApplicationId(), is(ApplicationId.defaultId()));
        assertFalse(prepareParams.isDryRun());
        assertFalse(prepareParams.ignoreValidationErrors());
        assertThat(prepareParams.getVespaVersion(), is(Optional.<String>empty()));
        assertTrue(prepareParams.getTimeoutBudget().hasTimeLeft());
        assertThat(prepareParams.getRotations().size(), is(0));
    }


    static final String rotation = "rotation-042.vespa.a02.yahoodns.net";
    static final String vespaVersion = "6.37.49";
    static final String request = "http://foo:19071/application/v2/tenant/foo/application/bar?" +
            PrepareParams.DRY_RUN_PARAM_NAME + "=true&" +
            PrepareParams.IGNORE_VALIDATION_PARAM_NAME + "=false&" +
            PrepareParams.APPLICATION_NAME_PARAM_NAME + "=baz&" +
            PrepareParams.VESPA_VERSION_PARAM_NAME + "=" + vespaVersion + "&" +
            PrepareParams.DOCKER_VESPA_IMAGE_VERSION_PARAM_NAME+ "=" + vespaVersion;

    @Test
    public void testCorrectParsingWithRotation() {
        PrepareParams prepareParams = createParams(request + "&" +
                        PrepareParams.ROTATIONS_PARAM_NAME + "=" + rotation,
                TenantName.from("foo"));

        assertThat(prepareParams.getApplicationId().serializedForm(), is("foo:baz:default"));
        assertTrue(prepareParams.isDryRun());
        assertFalse(prepareParams.ignoreValidationErrors());
        final Version expectedVersion = Version.fromString(vespaVersion);
        assertThat(prepareParams.getVespaVersion().get(), is(expectedVersion));
        assertTrue(prepareParams.getTimeoutBudget().hasTimeLeft());
        final Set<Rotation> rotations = prepareParams.getRotations();
        assertThat(rotations.size(), is(1));
        assertThat(rotations, contains(equalTo(new Rotation(rotation))));
        assertThat(prepareParams.getDockerVespaImageVersion().get(), is(expectedVersion));
    }

    @Test
    public void testCorrectParsingWithSeveralRotations() {
        final String rotationTwo = "rotation-043.vespa.a02.yahoodns.net";
        final String twoRotations = rotation + "," + rotationTwo;
        PrepareParams prepareParams = createParams(request + "&" +
                        PrepareParams.ROTATIONS_PARAM_NAME + "=" + twoRotations,
                TenantName.from("foo"));
        final Set<Rotation> rotations = prepareParams.getRotations();
        assertThat(rotations, containsInAnyOrder(new Rotation(rotation), new Rotation(rotationTwo)));
    }

    // Create PrepareParams from a request (based on uri and tenant name)
    private static PrepareParams createParams(String uri, TenantName tenantName) {
        return PrepareParams.fromHttpRequest(
                HttpRequest.createTestRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.PUT),
                tenantName,
                Duration.ofSeconds(60));
    }
}
