// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.application.api.ApplicationFile;
import java.util.logging.Level;

import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;

import java.util.*;

/**
 * Status and md5sum for files within an application package.
 *
 * @author hmusum
 */
class SessionContentStatusListResponse extends SlimeJsonResponse {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger("SessionContentStatusListResponse");

    public SessionContentStatusListResponse(String urlBase, List<ApplicationFile> files) {
        Cursor array = slime.setArray();
        for (ApplicationFile f : files) {
            Cursor element = array.addObject();
            element.setString("status", f.getMetaData().getStatus());
            element.setString("md5", f.getMetaData().getMd5());
            element.setString("name", urlBase + f.getPath());
            log.log(Level.FINE, () -> "Adding file " + urlBase + f.getPath());
        }
    }

}
