// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.config.model.MapConfigModelRegistry;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.ModelBuilderAddingAccessControlFilter;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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


    @Test
    void validator_fails_with_empty_access_control_filter_chain() throws IOException, SAXException {
        DeployState deployState = createDeployState();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        try {
            new AccessControlFilterValidator().validate(model, deployState);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("The 'access-control' feature is not available in open-source Vespa.", e.getMessage());
        }
    }

    @Test
    void validator_accepts_non_empty_access_control_filter_chain() throws IOException, SAXException {
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
