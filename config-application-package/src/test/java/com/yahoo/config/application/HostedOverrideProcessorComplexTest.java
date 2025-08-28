// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class HostedOverrideProcessorComplexTest {

    private static final String servicesFile = "src/test/resources/complex-app/services.xml";

    @Test
    public void testProdBetaUsWest2a() throws TransformerException {
        String expected =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. --><services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
                    <container id="docsgateway" version="1.0">
                        <nodes count="3">
                            <resources disk="1800Gb" disk-speed="fast" memory="96Gb" storage-type="local" vcpu="48"/>
                        </nodes>
                    </container>
                    <container id="qrs" version="1.0">
                        <nodes count="3" required="true">
                            <resources disk="64Gb" memory="32Gb" vcpu="16"/>
                        </nodes>
                        <search/>
                    </container>
                    <container id="visitor" version="1.0">
                        <nodes count="2">
                            <resources disk="32Gb" memory="16Gb" vcpu="8"/>
                        </nodes>
                        <search/>
                    </container>
                    <content id="all" version="1.0">
                        <nodes count="3" groups="3" required="true">
                            <resources disk="1800Gb" disk-speed="fast" memory="96Gb" storage-type="local" vcpu="48"/>
                        </nodes>
                        <redundancy>1</redundancy>
                    </content>
                    <content id="filedocument" version="1.0">
                        <nodes count="2" groups="2" required="true">
                            <resources disk="37Gb" memory="9Gb" vcpu="3"/>
                        </nodes>
                        <redundancy>1</redundancy>
                    </content>
                </services>
                """;
        assertOverride(InstanceName.from("beta1"),
                       Environment.prod,
                       RegionName.from("aws-us-west-2a"),
                       CloudName.GCP,
                       expected);
    }

    @Test
    public void testProdBetaUsEast1b() throws TransformerException {
        String expected =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. --><services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
                    <container id="docsgateway" version="1.0">
                        <nodes count="3">
                            <resources disk="1800Gb" disk-speed="fast" memory="96Gb" storage-type="local" vcpu="48"/>
                        </nodes>
                    </container>
                    <container id="qrs" version="1.0">
                        <nodes count="5" required="true">
                            <resources disk="64Gb" memory="32Gb" vcpu="16"/>
                        </nodes>
                        <search/>
                    </container>
                    <container id="visitor" version="1.0">
                        <nodes count="2">
                            <resources disk="32Gb" memory="16Gb" vcpu="8"/>
                        </nodes>
                        <search/>
                    </container>
                    <content id="all" version="1.0">
                        <nodes count="10" groups="10" required="true">
                            <resources disk="1800Gb" disk-speed="fast" memory="96Gb" storage-type="local" vcpu="48"/>
                        </nodes>
                        <redundancy>1</redundancy>
                    </content>
                    <content id="filedocument" version="1.0">
                        <nodes count="2" groups="2" required="true">
                            <resources disk="32Gb" memory="8Gb" vcpu="4"/>
                        </nodes>
                        <redundancy>1</redundancy>
                    </content>
                </services>
                """;
        assertOverride(InstanceName.from("beta1"),
                       Environment.prod,
                       RegionName.from("aws-us-east-1b"),
                       CloudName.AWS,
                       expected);
    }

    private void assertOverride(InstanceName instance, Environment environment, RegionName region, CloudName cloud, String expected) throws TransformerException {
        ApplicationPackage app = FilesApplicationPackage.fromDir(new File(servicesFile).getParentFile(), Map.of());
        Document inputDoc = Xml.getDocument(app.getServices());
        Tags tags = app.getDeploymentSpec().tags(instance, environment);
        Document newDoc = new OverrideProcessor(instance, environment, region, cloud, tags).process(inputDoc);
        assertEquals(expected, Xml.documentAsString(newDoc, true));
    }

}
