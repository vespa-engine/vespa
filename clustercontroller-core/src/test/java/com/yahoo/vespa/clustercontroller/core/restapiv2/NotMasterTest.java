// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.OtherMasterException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.UnknownMasterException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import org.junit.jupiter.api.Test;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NotMasterTest extends StateRestApiTest {

    @Test
    void testUnknownMaster() throws Exception {
        setUp(true);
        music.fleetControllerMaster = null;
        // Non-recursive cluster list works, as it doesn't touches into fleetcontrollers
        {
            UnitResponse response = restAPI.getState(new StateRequest("", 0));
            assertEquals("""
                         {
                           "cluster" : {
                             "books" : {
                               "link" : "/cluster/v2/books"
                             },
                             "music" : {
                               "link" : "/cluster/v2/music"
                             }
                           }
                         }""", jsonWriter.createJson(response).toPrettyString());
        }
        // Recursive cluster list does not work
        try {
            restAPI.getState(new StateRequest("", 1));
            fail();
        } catch (UnknownMasterException e) {
            assertTrue(e.getMessage().contains("No known master cluster controller"), e.getMessage());
        }
        // Other requests does not work either
        try {
            restAPI.getState(new StateRequest("music", 0));
            fail();
        } catch (UnknownMasterException e) {
            assertTrue(e.getMessage().contains("No known master cluster controller"), e.getMessage());
        }
        try {
            restAPI.getState(new StateRequest("music/storage", 0));
            fail();
        } catch (UnknownMasterException e) {
            assertTrue(e.getMessage().contains("No known master cluster controller"), e.getMessage());
        }
        try {
            restAPI.getState(new StateRequest("music/storage/1", 0));
            fail();
        } catch (UnknownMasterException e) {
            assertTrue(e.getMessage().contains("No known master cluster controller"), e.getMessage());
        }
        try {
            restAPI.setUnitState(new SetNodeStateTest.SetUnitStateRequestImpl("music/storage/1")
                    .setNewState("user", "down", "test"));
            fail();
        } catch (UnknownMasterException e) {
            assertTrue(e.getMessage().contains("No known master cluster controller"), e.getMessage());
        }
    }

    @Test
    void testKnownOtherMaster() throws Exception {
        setUp(true);
        ccSockets.put(1, new ClusterControllerStateRestAPI.Socket("otherhost", getDefaults().vespaWebServicePort()));
        music.fleetControllerMaster = 1;
        // Non-recursive cluster list works, as it doesn't touches into fleetcontrollers
        {
            UnitResponse response = restAPI.getState(new StateRequest("", 0));
            assertEquals("""
                         {
                           "cluster" : {
                             "books" : {
                               "link" : "/cluster/v2/books"
                             },
                             "music" : {
                               "link" : "/cluster/v2/music"
                             }
                           }
                         }""",
                         jsonWriter.createJson(response).toPrettyString());
        }
        // Recursive cluster list does not work
        try {
            restAPI.getState(new StateRequest("", 1));
            fail();
        } catch (OtherMasterException e) {
            assertTrue(e.getMessage().contains("Cluster controller not master. Use master at otherhost:" + getDefaults().vespaWebServicePort() + "."), e.getMessage());
            assertEquals("otherhost", e.getHost());
            assertEquals(e.getPort(), getDefaults().vespaWebServicePort());
        }
        // Other requests does not work either
        try {
            restAPI.getState(new StateRequest("music", 0));
            fail();
        } catch (OtherMasterException e) {
            assertTrue(e.getMessage().contains("Cluster controller not master. Use master at otherhost:" + getDefaults().vespaWebServicePort() + "."), e.getMessage());
            assertEquals("otherhost", e.getHost());
            assertEquals(e.getPort(), getDefaults().vespaWebServicePort());
        }
        try {
            restAPI.getState(new StateRequest("music/storage", 0));
            fail();
        } catch (OtherMasterException e) {
            assertTrue(e.getMessage().contains("Cluster controller not master. Use master at otherhost:" + getDefaults().vespaWebServicePort() + "."), e.getMessage());
            assertEquals("otherhost", e.getHost());
            assertEquals(e.getPort(), getDefaults().vespaWebServicePort());
        }
        try {
            restAPI.getState(new StateRequest("music/storage/1", 0));
            fail();
        } catch (OtherMasterException e) {
            assertTrue(e.getMessage().contains("Cluster controller not master. Use master at otherhost:" + getDefaults().vespaWebServicePort() + "."), e.getMessage());
            assertEquals("otherhost", e.getHost());
            assertEquals(e.getPort(), getDefaults().vespaWebServicePort());
        }
        try {
            restAPI.setUnitState(new SetNodeStateTest.SetUnitStateRequestImpl("music/storage/1")
                    .setNewState("user", "down", "test"));
            fail();
        } catch (OtherMasterException e) {
            assertTrue(e.getMessage().contains("Cluster controller not master. Use master at otherhost:" + getDefaults().vespaWebServicePort() + "."), e.getMessage());
            assertEquals("otherhost", e.getHost());
            assertEquals(e.getPort(), getDefaults().vespaWebServicePort());
        }
    }
}
