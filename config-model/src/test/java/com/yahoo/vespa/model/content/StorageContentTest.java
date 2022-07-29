// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.documentapi.messagebus.protocol.DocumentrouteselectorpolicyConfig;
import com.yahoo.messagebus.routing.RouteSpec;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.routing.DocumentProtocol;
import com.yahoo.vespa.model.routing.Routing;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

public class StorageContentTest extends ContentBaseTest {
    // TODO: Test with document-definitions

    private String createStorageVespaServices(String cluster1docs, String cluster2docs) {
        return "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services version='1.0'>" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='node0'/>" +
                "  </admin>" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>1</redundancy>\n" +
                cluster1docs +
                "     <group>" +
                "       <node hostalias='node0' distribution-key='0' />" +
                "     </group>" +
                "   </content>" +
                "   <content version='1.0' id='zoo'>" +
                "     <redundancy>1</redundancy>\n" +
                cluster2docs +
                "     <group>" +
                "       <node hostalias='node0' distribution-key='0' />" +
                "     </group>" +
                "</content>" +
                "</services>";
    }

    private VespaModel getStorageVespaModel(String cluster1docs, String cluster2docs) {
        List<String> sds = ApplicationPackageUtils.generateSchemas("type1", "type2", "type3");
        return new VespaModelCreatorWithMockPkg(getHosts(), createStorageVespaServices(cluster1docs, cluster2docs), sds).create();
    }

    public void doTestRouting(String cluster1docs, String cluster2docs, String expectedRoutes) throws Exception {
        VespaModel model = getStorageVespaModel(cluster1docs, cluster2docs);

        if (expectedRoutes == null) {
            return;
        }

        Routing routing = model.getRouting();
        assertNotNull(routing);

        assertEquals(0, routing.getErrors().size());
        assertEquals(1, routing.getProtocols().size());
        DocumentProtocol protocol = (DocumentProtocol) routing.getProtocols().get(0);

        RoutingTableSpec spec = protocol.getRoutingTableSpec();
        assertEquals(1, spec.getNumHops());
        assertEquals("indexing", spec.getHop(0).getName());
        assertEquals("[DocumentRouteSelector]", spec.getHop(0).getSelector());

        Map<String, RouteSpec> routes = new TreeMap<>();

        for (int i = 0; i < spec.getNumRoutes(); ++i) {
            RouteSpec r = spec.getRoute(i);

            routes.put(r.getName(), r);
        }

        {
            RouteSpec r = routes.get("default");
            assertEquals(1, r.getNumHops());
            assertEquals("indexing", r.getHop(0));
        }

        Set<String> configuredRoutes = new TreeSet<>();

        DocumentrouteselectorpolicyConfig.Builder builder = new DocumentrouteselectorpolicyConfig.Builder();
        protocol.getConfig(builder);
        DocumentrouteselectorpolicyConfig config = new DocumentrouteselectorpolicyConfig(builder);

        for (DocumentrouteselectorpolicyConfig.Route r : config.route()) {
            configuredRoutes.add(r.name() + " : " + r.selector());
        }

        StringBuilder routeStr = new StringBuilder();
        for (String r : configuredRoutes) {
            routeStr.append(r).append('\n');
        }

        assertEquals(expectedRoutes, routeStr.toString());
    }

    @Test
    void testDocumentTypesRouting() throws Exception {
        String cluster1docs = "<documents>\n" +
                "  <document type=\"type1\" mode=\"store-only\"/>\n" +
                "  <document type=\"type2\" mode=\"store-only\"/>\n" +
                "</documents>\n";
        String cluster2docs = "<documents>\n" +
                "  <document type=\"type3\" mode=\"store-only\"/>\n" +
                "</documents>\n";
        String expectedRoutes = "bar : (type1) OR (type2)\n" +
                "zoo : (type3)\n";

        doTestRouting(cluster1docs, cluster2docs, expectedRoutes);
    }

    @Test
    void testDocumentTypesAndLocalSelectionRouting() throws Exception {
        String cluster1docs = "<documents>\n" +
                "  <document type=\"type1\" mode=\"store-only\" selection=\"1 != 2\"/>\n" +
                "  <document type=\"type2\" mode=\"store-only\" selection=\"now() &gt; 1000\"/>\n" +
                "</documents>\n";
        String cluster2docs = "<documents>\n" +
                "  <document type=\"type3\" mode=\"store-only\" selection=\"true\"/>\n" +
                "</documents>\n";
        String expectedRoutes = "bar : (type1 AND (1 != 2)) OR (type2 AND (now() > 1000))\n" +
                "zoo : (type3 AND (true))\n";

        doTestRouting(cluster1docs, cluster2docs, expectedRoutes);
    }

    @Test
    void testDocumentTypesAndGlobalSelection() throws Exception {
        String cluster1docs = "<documents selection=\"5 != 6\">\n" +
                "  <document type=\"type1\" mode=\"store-only\" selection=\"type1.f1 == 'baz'\"/>\n" + // Can refer to own type
                "  <document type=\"type2\" mode=\"store-only\"/>\n" +
                "</documents>\n";
        String cluster2docs = "<documents selection=\"true\">\n" +
                "  <document type=\"type3\" mode=\"store-only\"/>\n" +
                "</documents>\n";
        String expectedRoutes = "bar : (5 != 6) AND ((type1 AND (type1.f1 == 'baz')) OR (type2))\n" +
                "zoo : (true) AND ((type3))\n";

        doTestRouting(cluster1docs, cluster2docs, expectedRoutes);
    }

    @Test
    void testIllegalDocumentTypesInSelection() throws Exception {
        String localDefs = "<documents>\n" +
                "  <document type=\"type1\" mode=\"store-only\"/>\n" +
                "  <document type=\"type2\" mode=\"store-only\" selection=\"type1.bar == 'baz'\"/>\n" + // Not own type
                "</documents>\n";
        String globalDefs = "<documents selection=\"type3.foo\">\n" + // No doctypes allowed
                "  <document type=\"type3\" mode=\"store-only\"/>\n" +
                "</documents>\n";
        String expectedRoutes = null;

        try {
            // Local
            doTestRouting(localDefs, localDefs, expectedRoutes);
            fail("no exception thrown for doc type in local selection");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Selection for document type 'type2" +
                    "' can not contain references to other " +
                    "document types (found reference to type 'type1')"));
        }

        try {
            // Global
            doTestRouting(globalDefs, globalDefs, expectedRoutes);
            fail("no exception thrown for doc type in global selection");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Document type references are not allowed"));
        }
    }
}
