// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.OtherMasterException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.UnknownMasterException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NotMasterTest extends StateRestApiTest {

    @Test
    public void testUnknownMaster() throws Exception {
        setUp(true);
        music.fleetControllerMaster = null;
            // Non-recursive cluster list works, as it doesn't touches into fleetcontrollers
        {
            UnitResponse response = restAPI.getState(new StateRequest("", 0));
            String expected =
                    "{\"cluster\": {\n" +
                            "  \"books\": {\"link\": \"\\/cluster\\/v2\\/books\"},\n" +
                            "  \"music\": {\"link\": \"\\/cluster\\/v2\\/music\"}\n" +
                            "}}";
            assertEquals(expected, jsonWriter.createJson(response).toString(2));
        }
            // Recursive cluster list does not work
        try{
            restAPI.getState(new StateRequest("", 1));
            fail();
        } catch (UnknownMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No known master cluster controller"));
        }
            // Other requests does not work either
        try{
            restAPI.getState(new StateRequest("music", 0));
            fail();
        } catch (UnknownMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No known master cluster controller"));
        }
        try{
            restAPI.getState(new StateRequest("music/storage", 0));
            fail();
        } catch (UnknownMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No known master cluster controller"));
        }
        try{
            restAPI.getState(new StateRequest("music/storage/1", 0));
            fail();
        } catch (UnknownMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No known master cluster controller"));
        }
        try{
            restAPI.getState(new StateRequest("music/storage/1/0", 0));
            fail();
        } catch (UnknownMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No known master cluster controller"));
        }
        try{
            restAPI.setUnitState(new SetNodeStateTest.SetUnitStateRequestImpl("music/storage/1")
                                    .setNewState("user", "down", "test"));
            fail();
        } catch (UnknownMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No known master cluster controller"));
        }
    }

    @Test
    public void testKnownOtherMaster() throws Exception {
        setUp(true);
        ccSockets.put(1, new ClusterControllerStateRestAPI.Socket("otherhost", getDefaults().vespaWebServicePort()));
        music.fleetControllerMaster = 1;
        // Non-recursive cluster list works, as it doesn't touches into fleetcontrollers
        {
            UnitResponse response = restAPI.getState(new StateRequest("", 0));
            String expected =
                    "{\"cluster\": {\n" +
                    "  \"books\": {\"link\": \"\\/cluster\\/v2\\/books\"},\n" +
                    "  \"music\": {\"link\": \"\\/cluster\\/v2\\/music\"}\n" +
                    "}}";
            assertEquals(expected, jsonWriter.createJson(response).toString(2));
        }
        // Recursive cluster list does not work
        try{
            restAPI.getState(new StateRequest("", 1));
            fail();
        } catch (OtherMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Cluster controller not master. Use master at otherhost:" + getDefaults().vespaWebServicePort() + "."));
            assertEquals("otherhost", e.getHost());
            assertEquals(e.getPort(), getDefaults().vespaWebServicePort());
        }
        // Other requests does not work either
        try{
            restAPI.getState(new StateRequest("music", 0));
            fail();
        } catch (OtherMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Cluster controller not master. Use master at otherhost:" + getDefaults().vespaWebServicePort() + "."));
            assertEquals("otherhost", e.getHost());
            assertEquals(e.getPort(), getDefaults().vespaWebServicePort());
        }
        try{
            restAPI.getState(new StateRequest("music/storage", 0));
            fail();
        } catch (OtherMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Cluster controller not master. Use master at otherhost:" + getDefaults().vespaWebServicePort() + "."));
            assertEquals("otherhost", e.getHost());
            assertEquals(e.getPort(), getDefaults().vespaWebServicePort());
        }
        try{
            restAPI.getState(new StateRequest("music/storage/1", 0));
            fail();
        } catch (OtherMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Cluster controller not master. Use master at otherhost:" + getDefaults().vespaWebServicePort() + "."));
            assertEquals("otherhost", e.getHost());
            assertEquals(e.getPort(), getDefaults().vespaWebServicePort());
        }
        try{
            restAPI.getState(new StateRequest("music/storage/1/0", 0));
            fail();
        } catch (OtherMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Cluster controller not master. Use master at otherhost:" + getDefaults().vespaWebServicePort() + "."));
            assertEquals("otherhost", e.getHost());
            assertEquals(e.getPort(), getDefaults().vespaWebServicePort());
        }
        try{
            restAPI.setUnitState(new SetNodeStateTest.SetUnitStateRequestImpl("music/storage/1")
                    .setNewState("user", "down", "test"));
            fail();
        } catch (OtherMasterException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Cluster controller not master. Use master at otherhost:" + getDefaults().vespaWebServicePort() + "."));
            assertEquals("otherhost", e.getHost());
            assertEquals(e.getPort(), getDefaults().vespaWebServicePort());
        }
    }
}
