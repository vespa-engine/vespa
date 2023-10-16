// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
public record TrialNotifications(List<Status> tenants) {
    private static final Logger log = Logger.getLogger(TrialNotifications.class.getName());

    public TrialNotifications { tenants = List.copyOf(tenants); }

    public record Status(TenantName tenant, State state, Instant lastUpdate) {}
    public enum State { SIGNED_UP, MID_CHECK_IN, EXPIRES_SOON, EXPIRES_IMMEDIATELY, EXPIRED, UNKNOWN }

    public Slime toSlime() {
        var slime = new Slime();
        var rootCursor = slime.setObject();
        var tenantsCursor = rootCursor.setArray("tenants");
        for (Status t : tenants) {
            var tenantCursor = tenantsCursor.addObject();
            tenantCursor.setString("tenant", t.tenant().value());
            tenantCursor.setString("state", t.state().name());
            tenantCursor.setString("lastUpdate", t.lastUpdate().toString());
        }
        log.fine(() -> "Generated json '%s' from '%s'".formatted(SlimeUtils.toJson(slime), this));
        return slime;
    }

    public static TrialNotifications fromSlime(Slime slime) {
        var rootCursor = slime.get();
        var tenantsCursor = rootCursor.field("tenants");
        var tenants = new ArrayList<Status>();
        for (int i = 0; i < tenantsCursor.entries(); i++) {
            var tenantCursor = tenantsCursor.entry(i);
            var name = TenantName.from(tenantCursor.field("tenant").asString());
            var stateStr = tenantCursor.field("state").asString();
            var state = Arrays.stream(State.values())
                    .filter(s -> s.name().equals(stateStr)).findFirst().orElse(State.UNKNOWN);
            var lastUpdate = Instant.parse(tenantCursor.field("lastUpdate").asString());
            tenants.add(new Status(name, state, lastUpdate));
        }
        var tn = new TrialNotifications(tenants);
        log.fine(() -> "Parsed '%s' from '%s'".formatted(tn, SlimeUtils.toJson(slime)));
        return tn;
    }
}
