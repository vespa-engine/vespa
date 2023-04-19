package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RequireContainerValidatorTest {

    @Test
    void failsWhenNoContainerInApplication() throws IOException, SAXException {
        runValidatorOnApp("""
                           <container id='default' version='1.0'>
                             <nodes count='2' />
                           </container>
                           """);
        assertEquals("Applications must have at least one container cluster",
                     assertThrows(IllegalArgumentException.class,
                                  () -> runValidatorOnApp(""))
                             .getMessage());
    }

    private static void runValidatorOnApp(String container) throws IOException, SAXException {
        String servicesXml = """
                        <services version='1.0'>
                          %s
                          <content version='1.0'>
                            <redundancy>2</redundancy>
                            <documents>
                            </documents>
                            <nodes count='2' />
                          </content>
                        </services>
                """.formatted(container);
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .build();
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(app)
                .properties(new TestProperties().setAllowUserFilters(false))
                .build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        new RequireContainerValidator().validate(model, deployState);
    }

}
