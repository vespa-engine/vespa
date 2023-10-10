// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;


/**
 * Represents a response for a request to show the status and md5sum of a file in the application package.
 *
 * @author hmusum
 */
public class SessionContentStatusResponse extends SlimeJsonResponse {

    public SessionContentStatusResponse(ApplicationFile file, String urlBase) {
        ApplicationFile.MetaData metaData;
        if (file == null) {
            metaData = new ApplicationFile.MetaData(ApplicationFile.ContentStatusDeleted, "");
        } else {
            metaData = file.getMetaData();
        }
        if (metaData == null) {
            throw new IllegalArgumentException("Could not find status for '" + file.getPath() + "'");
        }

        Cursor element = slime.setObject();
        element.setString("status", metaData.getStatus());
        element.setString("md5", metaData.getMd5());
        element.setString("name", urlBase + file.getPath());
    }

}
