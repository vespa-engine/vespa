package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.text.XML;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.TokenId;
import com.yahoo.vespa.hosted.controller.application.pkg.BasicServicesXml.Container;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
class BasicServicesXmlTest {

    @Test
    public void parse() {
        assertServices(new BasicServicesXml(List.of()), "<services/>");
        assertServices(new BasicServicesXml(List.of(new Container("foo", List.of(Container.AuthMethod.mtls), List.of()),
                                                    new Container("bar", List.of(Container.AuthMethod.mtls), List.of()))),
                       """
                               <services>
                                 <container id="foo"/>
                                 <container id="bar"/>
                               </services>
                               """);
        assertServices(new BasicServicesXml(List.of(
                               new Container("foo",
                                             List.of(Container.AuthMethod.mtls,
                                                     Container.AuthMethod.token),
                                             List.of(TokenId.of("my-token"),
                                                     TokenId.of("other-token"))),
                               new Container("bar", List.of(Container.AuthMethod.mtls), List.of()))),
                       """
                               <services>
                                 <container id="foo">
                                   <clients>
                                     <client id="mtls"/>
                                     <client id="token">
                                       <token id="my-token"/>
                                     </client>
                                     <client id="token2">
                                       <token id="other-token"/>
                                     </client>
                                   </clients>
                                 </container>
                                 <container id="bar"/>
                               </services>
                               """);
    }

    private void assertServices(BasicServicesXml expected, String xmlForm) {
        assertEquals(expected, BasicServicesXml.parse(XML.getDocument(xmlForm)));
    }

}
