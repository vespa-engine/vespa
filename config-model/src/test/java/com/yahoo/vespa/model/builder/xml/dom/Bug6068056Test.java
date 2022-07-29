// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class Bug6068056Test {
    private final static String HOSTS = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<hosts>" +
            "  <host name=\"localhost\">" +
            "    <alias>node1</alias>" +
            "  </host>" +
            "</hosts>";

    private final static String SERVICES = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<services>" +
            "  <admin version=\"2.0\">" +
            "    <adminserver hostalias=\"node1\" />" +
            "  </admin>" +

            "  <container id=\"docproc\" version=\"1.0\">" +
            "    <search/>" +
            "    <document-processing/>" +
            "    <nodes>" +
            "      <node hostalias=\"node1\"/>" +
            "    </nodes>" +
            "  </container>" +

            "<content version='1.0' id='music'>\n" +
            "     <redundancy>1</redundancy>\n" +
            "     <documents/>\n" +
            "     <group name='mygroup'>\n" +
            "       <node hostalias='node1' distribution-key='0'/>\n" +
            "     </group>\n" +
            "     <engine>\n" +
            "       <proton>\n" +
            "         <searchable-copies>1</searchable-copies>\n" +
            "       </proton>\n" +
            "     </engine>\n" +
            "   </content>" +
            "</services>";

    @Test
    void testContainerClusterCalledDocproc() {
        assertThrows(RuntimeException.class, () -> {
            VespaModelCreatorWithMockPkg creator = new VespaModelCreatorWithMockPkg(HOSTS, SERVICES);
            creator.create();
        });
    }
}
