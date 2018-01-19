// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * Status and md5sum for files within an application package.
 *
 * @author hmusum
 */
class SessionContentStatusListResponse extends SessionResponse {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger("SessionContentStatusListResponse");

    private final Slime slime = new Slime();

    public SessionContentStatusListResponse(String urlBase, List<ApplicationFile> files) {
        super();
        Cursor array = slime.setArray();
        for (ApplicationFile f : files) {
            Cursor element = array.addObject();
            element.setString("status", f.getMetaData().getStatus());
            element.setString("md5", f.getMetaData().getMd5());
            element.setString("name", urlBase + f.getPath());
            log.log(LogLevel.DEBUG, "Adding file " + urlBase + f.getPath());
        }
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        new JsonFormat(true).encode(outputStream, slime);
    }

}
