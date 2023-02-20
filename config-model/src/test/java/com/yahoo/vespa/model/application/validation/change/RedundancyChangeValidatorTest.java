package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static com.yahoo.test.JunitCompat.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class RedundancyChangeValidatorTest {

    private static final String redundancyOverride =
            "<validation-overrides>\n" +
            "    <allow until='2000-01-03'>redundancy-one</allow>\n" +
            "</validation-overrides>\n";

    @Test
    public void testChangingRedundancyToOne() {
        try {
            var tester = new ValidationTester(6);
            VespaModel previous = tester.deploy(null, getServices("test", 2), Environment.prod, null).getFirst();
            tester.deploy(previous, getServices("test", 1), Environment.prod, null);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("redundancy-one: content cluster 'test' has redundancy 1, which will cause it to lose data if a node fails. " +
                         "This requires an override on first deployment in a production zone. " +
                         "To allow this add <allow until='yyyy-mm-dd'>redundancy-one</allow> to validation-overrides.xml, " +
                         "see https://docs.vespa.ai/en/reference/validation-overrides.html",
                         Exceptions.toMessageString(e));
        }

    }

    @Test
    public void testChangingRedundancyToOneWithValidationOverride() {
        var tester = new ValidationTester(6);
        VespaModel previous = tester.deploy(null, getServices("test", 2), Environment.prod, null).getFirst();
        previous = tester.deploy(previous, getServices("test", 1), Environment.prod, redundancyOverride).getFirst();

        // Staying at one does not require an override
        tester.deploy(previous, getServices("test", 1), Environment.prod, null);
    }

    private static String getServices(String contentClusterId, int redundancy) {
        return "<services version='1.0'>" +
               "  <content id='" + contentClusterId + "' version='1.0'>" +
               "    <redundancy>" + redundancy + "</redundancy>" +
               "    <engine>" +
               "    <proton/>" +
               "    </engine>" +
               "    <documents>" +
               "      <document type='music' mode='index'/>" +
               "    </documents>" +
               "    <nodes count='2'/>" +
               "   </content>" +
               "</services>";
    }

}
