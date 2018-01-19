// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.application.api.ApplicationFile;

import java.io.*;

/**
 * Represents a response for a request to show the status and md5sum of a file in the application package.
 *
 * @author hmusum
 */
public class SessionContentStatusResponse extends SessionResponse {

    private final ApplicationFile file;
    private final String urlBase;
    private final ApplicationFile.MetaData metaData;
    private final ObjectMapper mapper = new ObjectMapper();

    public SessionContentStatusResponse(ApplicationFile file, String urlBase) {
        super();
        this.file = file;
        this.urlBase = urlBase;

        ApplicationFile.MetaData metaData;
        if (file == null) {
            metaData = new ApplicationFile.MetaData(ApplicationFile.ContentStatusDeleted, "");
        } else {
            metaData = file.getMetaData();
        }
        if (metaData == null) {
            throw new IllegalArgumentException("Could not find status for '" + file.getPath() + "'");
        }
        this.metaData = metaData;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        mapper.writeValue(outputStream, new ResponseData(metaData.status, metaData.md5, urlBase + file.getPath()));
    }

    private static class ResponseData {
        public final String status;
        public final String md5;
        public final String name;

        private ResponseData(String status, String md5, String name) {
            this.status = status;
            this.md5 = md5;
            this.name = name;
        }
    }

}
