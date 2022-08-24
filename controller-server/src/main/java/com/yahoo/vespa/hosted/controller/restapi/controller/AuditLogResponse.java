// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLog;

/**
 * @author mpolden
 */
public class AuditLogResponse extends SlimeJsonResponse {

    public AuditLogResponse(AuditLog log) {
        super(toSlime(log));
    }

    private static Slime toSlime(AuditLog log) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor entryArray = root.setArray("entries");
        log.entries().forEach(entry -> {
            Cursor entryObject = entryArray.addObject();
            entryObject.setString("time", entry.at().toString());
            entryObject.setString("client", entry.client().name());
            entryObject.setString("user", entry.principal());
            entryObject.setString("method", entry.method().name());
            entryObject.setString("resource", entry.resource());
            entry.data().ifPresent(data -> entryObject.setString("data", data));
        });
        return slime;
    }

}
