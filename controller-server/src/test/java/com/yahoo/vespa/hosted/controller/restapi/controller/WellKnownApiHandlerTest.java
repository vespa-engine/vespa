package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.application.container.handler.Request;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author olaa
 */
class WellKnownApiHandlerTest extends ControllerContainerTest  {

    private ContainerTester tester;
    private final String SECURITY_TXT = "Mocked security txt";

    @BeforeEach
    public void before() {
        tester = new ContainerTester(container, "src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/");
    }

    @Test
    void securityTxt() {
        tester.assertResponse(new Request("http://localhost:8080/.well-known/security.txt"), SECURITY_TXT);
    }

    @Override
    protected String variablePartXml() {
        return String.format("""
                <component id='com.yahoo.vespa.hosted.controller.security.CloudAccessControlRequests'/>
                <component id='com.yahoo.vespa.hosted.controller.security.CloudAccessControl'/>
                <handler id="com.yahoo.vespa.hosted.controller.restapi.controller.WellKnownApiHandler" bundle="controller-clients" >
                  <config name="vespa.hosted.controller.config.well-known-folder">
                    <securityTxt>%s</securityTxt>
                  </config>
                  <binding>http://*/.well-known/*</binding>
                </handler>
                <http>
                  <server id='default' port='8080' />
                  <filtering>
                    <request-chain id='default'>
                      <filter id='com.yahoo.jdisc.http.filter.security.misc.NoopFilter'/>
                      <binding>http://*/*</binding>
                    </request-chain>
                  </filtering>
                </http>
                """, SECURITY_TXT);
    }

}