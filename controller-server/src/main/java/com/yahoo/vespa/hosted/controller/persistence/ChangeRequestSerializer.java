// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequest;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestSource;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.HostAction;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.VespaChangeRequest;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author olaa
 */
public class ChangeRequestSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String ID_FIELD = "id";
    private static final String SOURCE_FIELD = "source";
    private static final String SOURCE_SYSTEM_FIELD = "system";
    private static final String STATUS_FIELD = "status";
    private static final String URL_FIELD = "url";
    private static final String ZONE_FIELD = "zoneId";
    private static final String START_TIME_FIELD = "plannedStartTime";
    private static final String END_TIME_FIELD = "plannedEndTime";
    private static final String APPROVAL_FIELD = "approval";
    private static final String IMPACT_FIELD = "impact";
    private static final String IMPACTED_HOSTS_FIELD = "impactedHosts";
    private static final String IMPACTED_SWITCHES_FIELD = "impactedSwitches";
    private static final String ACTION_PLAN_FIELD = "actionPlan";
    private static final String HOST_FIELD = "hostname";
    private static final String ACTION_STATE_FIELD = "state";
    private static final String LAST_UPDATED_FIELD = "lastUpdated";
    private static final String HOSTS_FIELD = "hosts";
    private static final String CATEGORY_FIELD = "category";


    public static VespaChangeRequest fromSlime(Slime slime) {
        var inspector = slime.get();
        var id = inspector.field(ID_FIELD).asString();
        var zoneId = ZoneId.from(inspector.field(ZONE_FIELD).asString());
        var changeRequestSource = readChangeRequestSource(inspector.field(SOURCE_FIELD));
        var actionPlan = readHostActionPlan(inspector.field(ACTION_PLAN_FIELD));
        var status = VespaChangeRequest.Status.valueOf(inspector.field(STATUS_FIELD).asString());
        var impact = ChangeRequest.Impact.valueOf(inspector.field(IMPACT_FIELD).asString());
        var approval = ChangeRequest.Approval.valueOf(inspector.field(APPROVAL_FIELD).asString());
        var category = inspector.field(CATEGORY_FIELD).valid() ?
                inspector.field(CATEGORY_FIELD).asString() : "Unknown";

        var impactedHosts = new ArrayList<String>();
        inspector.field(IMPACTED_HOSTS_FIELD)
                .traverse((ArrayTraverser) (i, hostname) -> impactedHosts.add(hostname.asString()));
        var impactedSwitches = new ArrayList<String>();
        inspector.field(IMPACTED_SWITCHES_FIELD)
                .traverse((ArrayTraverser) (i, switchName) -> impactedSwitches.add(switchName.asString()));

        return new VespaChangeRequest(
                id,
                changeRequestSource,
                impactedSwitches,
                impactedHosts,
                approval,
                impact,
                status,
                actionPlan,
                zoneId);
    }

    public static Slime toSlime(VespaChangeRequest changeRequest) {
        var slime = new Slime();
        writeChangeRequest(slime.setObject(), changeRequest);
        return slime;
    }

    public static void writeChangeRequest(Cursor cursor, VespaChangeRequest changeRequest) {
        cursor.setString(ID_FIELD, changeRequest.getId());
        cursor.setString(STATUS_FIELD, changeRequest.getStatus().name());
        cursor.setString(IMPACT_FIELD, changeRequest.getImpact().name());
        cursor.setString(APPROVAL_FIELD, changeRequest.getApproval().name());
        cursor.setString(ZONE_FIELD, changeRequest.getZoneId().value());
        writeChangeRequestSource(cursor.setObject(SOURCE_FIELD), changeRequest.getChangeRequestSource());
        writeActionPlan(cursor.setObject(ACTION_PLAN_FIELD), changeRequest);

        var impactedHosts = cursor.setArray(IMPACTED_HOSTS_FIELD);
        changeRequest.getImpactedHosts().forEach(impactedHosts::addString);
        var impactedSwitches = cursor.setArray(IMPACTED_SWITCHES_FIELD);
        changeRequest.getImpactedSwitches().forEach(impactedSwitches::addString);
    }

    private static void writeActionPlan(Cursor cursor, VespaChangeRequest changeRequest) {
        var hostsCursor = cursor.setArray(HOSTS_FIELD);

        changeRequest.getHostActionPlan().forEach(action -> {
            var actionCursor = hostsCursor.addObject();
            actionCursor.setString(HOST_FIELD, action.getHostname());
            actionCursor.setString(ACTION_STATE_FIELD, action.getState().name());
            actionCursor.setString(LAST_UPDATED_FIELD, action.getLastUpdated().toString());
        });

        // TODO: Add action plan per application
    }

    private static void writeChangeRequestSource(Cursor cursor, ChangeRequestSource source) {
        cursor.setString(SOURCE_SYSTEM_FIELD, source.system());
        cursor.setString(ID_FIELD, source.id());
        cursor.setString(URL_FIELD, source.url());
        cursor.setString(START_TIME_FIELD, source.plannedStartTime().toString());
        cursor.setString(END_TIME_FIELD, source.plannedEndTime().toString());
        cursor.setString(STATUS_FIELD, source.status().name());
        cursor.setString(CATEGORY_FIELD, source.category());
    }

    public static ChangeRequestSource readChangeRequestSource(Inspector inspector) {
        var category = inspector.field(CATEGORY_FIELD).valid() ?
                inspector.field(CATEGORY_FIELD).asString() : "Unknown";
        return new ChangeRequestSource(
                inspector.field(SOURCE_SYSTEM_FIELD).asString(),
                inspector.field(ID_FIELD).asString(),
                inspector.field(URL_FIELD).asString(),
                ChangeRequestSource.Status.valueOf(inspector.field(STATUS_FIELD).asString()),
                ZonedDateTime.parse(inspector.field(START_TIME_FIELD).asString()),
                ZonedDateTime.parse(inspector.field(END_TIME_FIELD).asString()),
                category
        );
    }

    public static List<HostAction> readHostActionPlan(Inspector inspector) {
        if (!inspector.valid())
            return List.of();

        var actionPlan = new ArrayList<HostAction>();
        inspector.field(HOSTS_FIELD).traverse((ArrayTraverser) (index, hostObject) ->
            actionPlan.add(
                    new HostAction(
                            hostObject.field(HOST_FIELD).asString(),
                            HostAction.State.valueOf(hostObject.field(ACTION_STATE_FIELD).asString()),
                            Instant.parse(hostObject.field(LAST_UPDATED_FIELD).asString())
                    )
            )
        );
        return actionPlan;
    }

}
