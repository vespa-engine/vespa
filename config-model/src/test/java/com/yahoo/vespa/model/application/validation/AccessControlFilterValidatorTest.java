package com.yahoo.vespa.model.application.validation;// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.config.model.MapConfigModelRegistry;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.ModelBuilderAddingAccessControlFilter;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * @author bjorncs
 */
public class AccessControlFilterValidatorTest {

    private static final String SERVICES_XML = String.join(
            "\n",
            "<services version='1.0'>",
            "  <container id='container-cluster-with-access-control' version='1.0'>",
            "    <http>",
            "      <filtering>",
            "        <access-control domain='foo' read='true' write='true'/>",
            "      </filtering>",
            "    </http>",
            "  </container>",
            "</services>");

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void validator_fails_with_empty_access_control_filter_chain() throws IOException, SAXException {
        DeployState deployState = createDeployState();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The 'access-control' feature is not available in open-source Vespa.");
        new AccessControlFilterValidator().validate(model, deployState);
    }

    @Test
    public void validator_accepts_non_empty_access_control_filter_chain() throws IOException, SAXException {
        DeployState deployState = createDeployState();
        VespaModel model = new VespaModel(
                MapConfigModelRegistry.createFromList(new ModelBuilderAddingAccessControlFilter()),
                deployState);

        new AccessControlFilterValidator().validate(model, deployState);
    }

    private static DeployState createDeployState() {
        return new DeployState.Builder()
                .applicationPackage(new MockApplicationPackage.Builder().withServices(SERVICES_XML).build())
                .build();
    }
}