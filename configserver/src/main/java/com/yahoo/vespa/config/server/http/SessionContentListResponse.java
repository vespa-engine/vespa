// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;

import java.util.List;

/**
 * Represents a request for listing files within an application package.
 *
 * @author Ulf Lilleengen
 */
class SessionContentListResponse extends SlimeJsonResponse {

    public SessionContentListResponse(String urlBase, List<ApplicationFile> files) {
        Cursor array = slime.setArray();
        for (ApplicationFile file : files) {
            array.addString(urlBase + file.getPath() + (file.isDirectory() ? "/" : ""));
        }
    }

}
