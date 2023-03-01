package com.yahoo.config.application;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.xml.DeploymentSpecXmlReader;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class HostedOverrideProcessorComplexTest {

    private static final String servicesFile = "src/test/resources/complex-app/services.xml";

    @Test
    public void testProdBetaUsWest2a() throws TransformerException, IOException {
        String expected =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
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
                        <nodes count="2" groups="2">
                            <resources disk="32Gb" memory="8Gb" vcpu="4"/>
                        </nodes>
                        <redundancy>1</redundancy>
                    </content>
                </services>
                """;
        assertOverride(InstanceName.from("beta1"),
                       Environment.prod,
                       RegionName.from("aws-us-west-2a"),
                       expected);
    }

    @Test
    public void testProdBetaUsEast1b() throws TransformerException, IOException {
        String expected =
                """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <services xmlns:deploy="vespa" xmlns:preprocess="properties" version="1.0">
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
                        <nodes count="2" groups="2">
                            <resources disk="32Gb" memory="8Gb" vcpu="4"/>
                        </nodes>
                        <redundancy>1</redundancy>
                    </content>
                </services>
                """;
        assertOverride(InstanceName.from("beta1"),
                       Environment.prod,
                       RegionName.from("aws-us-east-1b"),
                       expected);
    }

    private void assertOverride(InstanceName instance, Environment environment, RegionName region, String expected) throws TransformerException {
        ApplicationPackage app = FilesApplicationPackage.fromFile(new File(servicesFile).getParentFile());
        Document inputDoc = Xml.getDocument(app.getServices());
        Tags tags = app.getDeploymentSpec().instance(instance).map(DeploymentInstanceSpec::tags).orElse(Tags.empty());
        Document newDoc = new OverrideProcessor(instance, environment, region, tags).process(inputDoc);
        assertEquals(expected, Xml.documentAsString(newDoc, true));
    }

}
