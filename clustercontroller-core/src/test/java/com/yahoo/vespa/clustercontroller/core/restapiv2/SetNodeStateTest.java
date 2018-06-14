// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.MasterInterface;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTask;
import com.yahoo.vespa.clustercontroller.core.restapiv2.requests.SetNodeStateRequest;
import com.yahoo.vespa.clustercontroller.core.restapiv2.requests.WantedStateSetter;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.DeadlineExceededException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InvalidContentException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.MissingUnitException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.OperationNotSupportedForUnitException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.UnknownMasterException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.SetResponse;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SetNodeStateTest extends StateRestApiTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    public static class SetUnitStateRequestImpl extends StateRequest implements SetUnitStateRequest {
        private Map<String, UnitState> newStates = new LinkedHashMap<>();
        private Condition condition = Condition.FORCE;
        private ResponseWait responseWait = ResponseWait.WAIT_UNTIL_CLUSTER_ACKED;

        public SetUnitStateRequestImpl(String req) {
            super(req, 0);
        }

        public SetUnitStateRequestImpl setCondition(Condition condition) {
            this.condition = condition;
            return this;
        }

        public SetUnitStateRequestImpl setResponseWait(ResponseWait responseWait) {
            this.responseWait = responseWait;
            return this;
        }

        public SetUnitStateRequestImpl setNewState(
                final String type,
                final String state,
                final String reason) {
            newStates.put(type, new UnitState() {
                @Override
                public String getId() {
                    return state;
                }

                @Override
                public String getReason() {
                    return reason;
                }
            });
            return this;
        }

        @Override
        public Map<String, UnitState> getNewState() {
            return newStates;
        }

        @Override
        public Condition getCondition() {
            return condition;
        }

        @Override
        public ResponseWait getResponseWait() {
            return responseWait;
        }
    }

    private void verifyStateSet(String state, String reason) throws Exception {
        restAPI.setUnitState(new SetUnitStateRequestImpl(
                "music/distributor/1").setNewState("user", state, reason));
        UnitResponse response = restAPI.getState(new StateRequest("music/distributor/1", 0));
        String expected = musicClusterExpectedUserStateString("east.g2", "up", "up", state.toLowerCase(), reason);
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }

    private void verifyClusterSet(String state, String reason) throws Exception {
        restAPI.setUnitState(new SetUnitStateRequestImpl("music").setNewState("user", state, reason));
        for (int index : new int[]{1, 2, 3, 5, 7}) {
            UnitResponse response = restAPI.getState(new StateRequest("music/storage/" + index, 0));
            String actualState = response.getCurrentState().getStatePerType().get("user").getId();
            assertThat(actualState, is(state.toLowerCase()));
            String actualReason = response.getCurrentState().getStatePerType().get("user").getReason();
            assertThat(actualReason, is(reason));
        }
    }

    private String musicClusterExpectedUserStateStringWithUninitializedNode(String groupName,
                                                                            String generatedState, String unitState,
                                                                            String userState, String userReason) {
        return "{\n" +
        "  \"attributes\": {\"hierarchical-group\": \"" + groupName + "\"},\n" +
        "  \"state\": {\n" +
        "    \"generated\": {\n" +
        "      \"state\": \"" + generatedState + "\",\n" +
        "      \"reason\": \"\"\n" +
        "    },\n" +
        "    \"unit\": {\n" +
        "      \"state\": \"" + unitState + "\",\n" +
        "      \"reason\": \"Node not seen in slobrok.\"\n" +
        "    },\n" +
        "    \"user\": {\n" +
        "      \"state\": \"" + userState + "\",\n" +
        "      \"reason\": \"" + userReason + "\"\n" +
        "    }\n" +
        "  }\n" +
        "}";
    }

    private String musicClusterExpectedUserStateString(String groupName,
                                                       String generatedState, String unitState,
                                                       String userState, String userReason) {
        return "{\n" +
        "  \"attributes\": {\"hierarchical-group\": \"" + groupName + "\"},\n" +
        "  \"state\": {\n" +
        "    \"generated\": {\n" +
        "      \"state\": \"" + generatedState + "\",\n" +
        "      \"reason\": \"\"\n" +
        "    },\n" +
        "    \"unit\": {\n" +
        "      \"state\": \"" + unitState + "\",\n" +
        "      \"reason\": \"\"\n" +
        "    },\n" +
        "    \"user\": {\n" +
        "      \"state\": \"" + userState + "\",\n" +
        "      \"reason\": \"" + userReason + "\"\n" +
        "    }\n" +
        "  }\n" +
        "}";
    }

    @Test
    public void testSimple() throws Exception {
        setUp(true);
        verifyStateSet("down", "testing");
        verifyStateSet("up", "foo");
        verifyStateSet("maintenance", "");
        verifyStateSet("retired", "argh");
        verifyStateSet("UP", "even uppercase");
    }

    @Test
    public void testSetNodesForCluster() throws Exception {
        setUp(true);
        verifyClusterSet("maintenance", "prepare for maintenance");
        verifyClusterSet("up", "and we're back online");
    }

    @Test
    public void testShouldNotModifyDistributorSafe() throws Exception {
        setUp(false);
        SetResponse setResponse = restAPI.setUnitState(new SetUnitStateRequestImpl("music/distributor/1")
                .setNewState("user", "up", "whatever reason.")
                .setCondition(SetUnitStateRequest.Condition.SAFE));
        assertThat(setResponse.getWasModified(), is(false));
        assertThat(setResponse.getReason(), containsString(
                "Safe-set of node state is only supported for storage nodes"));
    }

    @Test
    public void testShouldModifyStorageSafeOk() throws Exception {
        setUp(false);
        SetResponse setResponse = restAPI.setUnitState(new SetUnitStateRequestImpl("music/storage/1")
                .setNewState("user", "maintenance", "whatever reason.")
                .setCondition(SetUnitStateRequest.Condition.SAFE));
        assertThat(setResponse.getWasModified(), is(true));
        assertThat(setResponse.getReason(), is("ok"));
    }

    @Test
    public void testShouldModifyStorageSafeBlocked() throws Exception {
        setUp(false);
        {
            SetResponse setResponse = restAPI.setUnitState(new SetUnitStateRequestImpl("music/storage/1")
                    .setNewState("user", "maintenance", "whatever reason.")
                    .setCondition(SetUnitStateRequest.Condition.SAFE));
            assertThat(setResponse.getReason(), is("ok"));
            assertThat(setResponse.getWasModified(), is(true));
        }
        {
            SetResponse setResponse = restAPI.setUnitState(new SetUnitStateRequestImpl("music/storage/3")
                    .setNewState("user", "maintenance", "whatever reason.")
                    .setCondition(SetUnitStateRequest.Condition.SAFE));
            assertThat(setResponse.getReason(), is(
                    "There is a node already in maintenance:1"));
            assertThat(setResponse.getWasModified(), is(false));
        }
    }

    @Test
    public void testSetWantedStateOnNodeNotInSlobrok() throws Exception {
        // Node 2 in cluster music does not have a valid NodeInfo due to passing true to setUp
        setUp(true);
        restAPI.setUnitState(new SetUnitStateRequestImpl("music/distributor/2").setNewState("user", "down", "borked node"));
        UnitResponse response = restAPI.getState(new StateRequest("music/distributor/2", 0));
        String expected = musicClusterExpectedUserStateStringWithUninitializedNode("east.g1", "down", "down", "down", "borked node");
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }

    @Test
    public void testWrongUnit() throws Exception {
        setUp(true);

        String wrongUnitMessage = "State can only be set at cluster or node level";
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "").setNewState("user", "down", "testing"));
            assertTrue(false);
        } catch (OperationNotSupportedForUnitException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(wrongUnitMessage));
        }

        // ... setting at cluster-level is allowed

        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/distributor").setNewState("user", "down", "testing"));
            assertTrue(false);
        } catch (OperationNotSupportedForUnitException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(wrongUnitMessage));
        }

        // ... setting at node-level is allowed

        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/storage/1/0").setNewState("user", "down", "testing"));
            assertTrue(false);
        } catch (OperationNotSupportedForUnitException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(wrongUnitMessage));
        }
    }

    @Test
    public void testInvalidUnit() throws Exception {
        setUp(true);
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "foo").setNewState("user", "down", "testing"));
            assertTrue(false);
        } catch (MissingUnitException e) {
        }
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/content").setNewState("user", "down", "testing"));
            assertTrue(false);
        } catch (MissingUnitException e) {
        }
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/storage/bah").setNewState("user", "down", "testing"));
            assertTrue(false);
        } catch (MissingUnitException e) {
        }
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/storage/10").setNewState("user", "down", "testing"));
            assertTrue(false);
        } catch (MissingUnitException e) {
        }
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/storage/1/0/1").setNewState("user", "down", "testing"));
            assertTrue(false);
        } catch (MissingUnitException e) {
        }
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/storage/1/bar").setNewState("user", "down", "testing"));
            assertTrue(false);
        } catch (MissingUnitException e) {
        }
    }

    @Test
    public void testSettingInvalidStateType() throws Exception {
        setUp(true);
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/distributor/1").setNewState("foo", "down", "testing"));
        } catch (InvalidContentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No new user state given"));
        }
    }

    @Test
    public void testSafeIsInvalidForSetNodesStatesForCluster() throws Exception {
        setUp(true);
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl("music")
                    .setNewState("user", "maintenance", "example reason")
                    .setCondition(SetUnitStateRequest.Condition.SAFE));
        } catch (InvalidContentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(
                    "Setting all nodes in a cluster to a state is only supported with FORCE"));
        }
    }

    @Test
    public void testSettingWrongStateType() throws Exception {
        setUp(true);
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/distributor/1").setNewState("generated", "down", "testing"));
        } catch (InvalidContentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No new user state given"));
        }
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/distributor/1").setNewState("unit", "down", "testing"));
        } catch (InvalidContentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No new user state given"));
        }
    }

    @Test
    public void testInvalidState() throws Exception {
        setUp(true);
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/distributor/1").setNewState("user", "initializing", "testing"));
        } catch (InvalidContentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Invalid user state"));
        }
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/distributor/1").setNewState("user", "stopping", "testing"));
        } catch (InvalidContentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Invalid user state"));
        }
        try{
            restAPI.setUnitState(new SetUnitStateRequestImpl(
                    "music/distributor/1").setNewState("user", "foo", "testing"));
        } catch (InvalidContentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Invalid user state"));
        }
    }

    @Test
    public void testOverwriteReason() throws Exception {
        setUp(true);
        restAPI.setUnitState(new SetUnitStateRequestImpl(
                "music/distributor/1").setNewState("user", "down", "testing"));
        restAPI.setUnitState(new SetUnitStateRequestImpl(
                "music/distributor/1").setNewState("user", "down", "testing more"));
        UnitResponse response = restAPI.getState(new StateRequest("music/distributor/1", 0));
        String expected = musicClusterExpectedUserStateString("east.g2", "up", "up", "down", "testing more");
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }

    private Id.Node createDummyId() {
        return new Id.Node(new Id.Service(new Id.Cluster("foo"), NodeType.STORAGE), 0);
    }

    private SetNodeStateRequest createDummySetNodeStateRequest() {
        return new SetNodeStateRequest(createDummyId(), new SetUnitStateRequestImpl("music/storage/1")
                .setNewState("user", "maintenance", "whatever reason."));
    }

    @Test
    public void set_node_state_requests_are_by_default_tagged_as_having_version_ack_dependency() {
        SetNodeStateRequest request = createDummySetNodeStateRequest();
        assertTrue(request.hasVersionAckDependency());
    }

    @Test
    public void set_node_state_requests_not_initially_marked_as_failed() {
        SetNodeStateRequest request = createDummySetNodeStateRequest();
        assertFalse(request.isFailed());
    }

    @Test
    public void set_node_state_requests_may_override_version_ack_dependency() {
        SetNodeStateRequest request = new SetNodeStateRequest(createDummyId(), new SetUnitStateRequestImpl("music/storage/1")
                .setNewState("user", "maintenance", "whatever reason.")
                .setResponseWait(SetUnitStateRequest.ResponseWait.NO_WAIT));
        assertFalse(request.hasVersionAckDependency());
    }

    // Technically, this failure mode currently applies to all requests, but it's only really
    // important to test (and expected to happen) for requests that have dependencies on cluster
    // state version publishing.
    @Test
    public void leadership_loss_fails_set_node_state_request() throws Exception {
        expectedException.expectMessage("Leadership lost before request could complete");
        expectedException.expect(UnknownMasterException.class);

        SetNodeStateRequest request = createDummySetNodeStateRequest();
        request.handleFailure(RemoteClusterControllerTask.FailureCondition.LEADERSHIP_LOST);
        request.getResult();
    }

    @Test
    public void leadership_loss_marks_request_as_failed_for_early_out_response() {
        SetNodeStateRequest request = createDummySetNodeStateRequest();
        request.handleFailure(RemoteClusterControllerTask.FailureCondition.LEADERSHIP_LOST);
        assertTrue(request.isFailed());
    }

    @Test
    public void deadline_exceeded_fails_set_node_state_request() throws Exception {
        expectedException.expectMessage("Task exceeded its version wait deadline");
        expectedException.expect(DeadlineExceededException.class);

        SetNodeStateRequest request = createDummySetNodeStateRequest();
        request.handleFailure(RemoteClusterControllerTask.FailureCondition.DEADLINE_EXCEEDED);
        request.getResult();
    }

    @Test
    public void no_fail_if_modified() throws StateRestApiException {
        assertFalse(isFailed(true));
    }

    @Test
    public void fail_if_not_modified() throws StateRestApiException {
        assertTrue(isFailed(false));
    }

    private boolean isFailed(boolean wasModified) throws StateRestApiException {
        WantedStateSetter wantedStateSetter = mock(WantedStateSetter.class);
        SetNodeStateRequest request = new SetNodeStateRequest(
                createDummyId(),
                new SetUnitStateRequestImpl("music/storage/1").setNewState("user", "maintenance", "whatever reason."),
                wantedStateSetter);
        SetResponse response = new SetResponse("some reason", wasModified);
        when(wantedStateSetter.set(any(), any(), any(), any(), any(), any())).thenReturn(response);

        RemoteClusterControllerTask.Context context = mock(RemoteClusterControllerTask.Context.class);
        MasterInterface masterInterface = mock(MasterInterface.class);
        context.masterInfo = masterInterface;
        when(masterInterface.isMaster()).thenReturn(true);
        request.doRemoteFleetControllerTask(context);
        return request.isFailed();
    }
}
