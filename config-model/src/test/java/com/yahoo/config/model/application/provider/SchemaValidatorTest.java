// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.component.Version;
import com.yahoo.vespa.config.VespaVersion;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hmusum
 */
public class SchemaValidatorTest {

    private static final String okServices = "<?xml version='1.0' encoding='utf-8' ?>\n" +
            "<services>\n" +
            "  <config name='standard'>\n" +
            "    <basicStruct>\n" +
            "      <stringVal>default</stringVal>\n" +
            "    </basicStruct>\n" +
            "  </config>\n" +
            "  <admin version='2.0'>\n" +
            "    <adminserver hostalias='node1' />\n" +
            "  </admin>\n" +
            "</services>\n";

    // Typo in closing end tag for <config> (<confih>)
    private static final String invalidServices = "<?xml version='1.0' encoding='utf-8' ?>\n" +
            "<services>\n" +
            "  <config name='standard'>\n" +
            "    <basicStruct>\n" +
            "      <stringVal>default</stringVal>\n" +
            "    </basicStruct>\n" +
            "  </confih>\n" +
            "  <admin version='2.0'>\n" +
            "    <adminserver hostalias='node1'>\n" +
            "  </admin>\n" +
            "</services>\n";

    private static String servicesWith(String element) {
        return "<?xml version='1.0' encoding='utf-8' ?>\n" +
               "<services>\n" +
               "  <" + element + " version='1.0'>\n" +
               "    <product document-type='model' namespace='model' id-field='id' />\n" +
               "    <category id-field='id' />\n" +
               "    <variant-presentation />\n" +
               "    <ranking-tags-registry />\n" +
               "  </" + element + ">\n" +
               "</services>\n";
    }

    @Test
    void testXMLParse() throws IOException {
        SchemaValidator validator = createValidator();
        validator.validate(new StringReader(okServices));
    }

    @Test
    void testProductDiscoveryIsAccepted() throws IOException {
        SchemaValidator validator = createValidator();
        validator.validate(new StringReader(servicesWith("product-discovery")));
    }

    @Test
    void testProductDiscoveryVersionIsMajorDotMinor() throws IOException {
        SchemaValidator validator = createValidator();
        String original = "<product-discovery version='1.0'>";

        // version must be major.minor
        String majorDotMinor = servicesWith("product-discovery").replace(original, "<product-discovery version='1.34'>");
        assertDoesNotThrow(() -> validator.validate(new StringReader(majorDotMinor)));

        // major.minor.micro is rejected
        String majorDotMinorDotMicro = servicesWith("product-discovery").replace(original, "<product-discovery version='1.0.0'>");
        assertThrows(RuntimeException.class, () -> validator.validate(new StringReader(majorDotMinorDotMicro)));
    }

    @Test
    void testProductDiscoveryGrammarOnlyAcceptsItsMajorVersion() throws IOException {
        SchemaValidator validator = createValidator();
        String original = "<product-discovery version='1.0'>";
        assertDoesNotThrow(() -> validator.validate(new StringReader(servicesWith("product-discovery").replace(original, "<product-discovery version='1.34'>"))));
        assertThrows(RuntimeException.class, () -> validator.validate(new StringReader(servicesWith("product-discovery").replace(original, "<product-discovery version='2.0'>"))));
    }

    @Test
    void testUnknownTopLevelElementIsRejected() {
        Throwable exception = assertThrows(RuntimeException.class, () -> {
            SchemaValidator validator = createValidator();
            validator.validate(new StringReader(servicesWith("commerce-discoverh")));
        });
        assertTrue(exception.getMessage().contains("element \"commerce-discoverh\" not allowed"));
    }

    @Test
    void testXMLParseError() {
        Throwable exception = assertThrows(RuntimeException.class, () -> {
            SchemaValidator validator = createValidator();
            validator.validate(new StringReader(invalidServices));
        });
        assertTrue(exception.getMessage().contains(expectedErrorMessage("input")));
    }

    @Test
    void testXMLParseWithReader() throws IOException {
        SchemaValidator validator = createValidator();
        validator.validate(new StringReader(okServices));
    }

    @Test
    void testXMLParseErrorWithReader() {
        Throwable exception = assertThrows(RuntimeException.class, () -> {
            SchemaValidator validator = createValidator();
            validator.validate(new StringReader(invalidServices));
        });
        assertTrue(exception.getMessage().contains(expectedErrorMessage("input")));
    }

    @Test
    void testXMLParseErrorFromFile() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SchemaValidator validator = createValidator();
            validator.validate(new File("src/test/cfg/application/invalid-services-syntax/services.xml"));
        });
        assertTrue(exception.getMessage().contains(expectedErrorMessage("services.xml")));
    }

    private SchemaValidator createValidator() {
        return new SchemaValidators(new Version(VespaVersion.major)).servicesXmlValidator();
    }

    private String expectedErrorMessage(String input) {
        return "Invalid XML according to XML schema, error in " + input + ": The element type \"config\" must be terminated by the matching end-tag \"</config>\". [7:5], input:\n" +
                "4:    <basicStruct>\n" +
                "5:      <stringVal>default</stringVal>\n" +
                "6:    </basicStruct>\n" +
                "7:  </confih>\n" +
                "8:  <admin version='2.0'>\n" +
                "9:    <adminserver hostalias='node1'>\n" +
                "10:  </admin>\n";
    }

    @Test
    void rejectsDoctypeDeclaration() {
        String withExternalDtd = """
                <?xml version='1.0' encoding='utf-8' ?>
                <!DOCTYPE services SYSTEM "foo">
                <services>
                  <admin version='2.0'>
                    <adminserver hostalias='node1' />
                  </admin>
                </services>
                """;
        Throwable exception = assertThrows(RuntimeException.class, () -> {
            SchemaValidator validator = createValidator();
            validator.validate(new StringReader(withExternalDtd));
        });
        assertTrue(exception.getMessage().contains("DOCTYPE"),
                   "Expected the parser to reject the DOCTYPE, got: " + exception.getMessage());
    }

    @Test
    void verifyValidFiles() throws Exception {
        justValidate("application.rnc", "application.xml");
        justValidate("application.rnc", "application.xml");
        justValidate("services.rnc", "services.xml");
        justValidate("services.rnc", "standalone-container.xml");
        justValidate("services.rnc", "services-hosted.xml");
        justValidate("services.rnc", "services-hosted-infrastructure.xml");
        justValidate("deployment.rnc", "deployment.xml");
        justValidate("deployment.rnc", "deployment-with-instances.xml");
        justValidate("validation-overrides.rnc", "validation-overrides.xml");
    }

    @Test
    void verifyInvalidFile() {
        Throwable exception = assertThrows(RuntimeException.class, () -> {
                justValidate("services.rnc", "services-bad-vespamalloc.xml");
            });
        assertTrue(exception.getMessage().contains("error in services-bad-vespamalloc.xml: value of attribute \"no-vespamalloc\" is invalid"));
    }

    private void justValidate(String rnc, String xml) throws Exception {
        String rncPre = "src/main/resources/schema/";
        String xmlPre = "src/test/schema-test-files/";
        var validator = fromRnc(new File(rncPre + rnc));
        validator.validate(new File(xmlPre + xml));
    }

    private SchemaValidator fromRnc(File rncFile) throws Exception {
        DeployLoggerStub logger = new DeployLoggerStub();
        return new SchemaValidator(rncFile, logger);
    }
}
