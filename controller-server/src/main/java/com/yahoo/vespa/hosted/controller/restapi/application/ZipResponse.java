// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A HTTP response containing a named ZIP file.
 *
 * @author mpolden
 */
public class ZipResponse extends HttpResponse {

    private final byte[] zipContent;

    public ZipResponse(String filename, byte[] zipContent) {
        super(200);
        this.zipContent = zipContent;
        this.headers().add("Content-Disposition", "attachment; filename=\"" + filename + "\"");
    }

    @Override
    public String getContentType() {
        return "application/zip";
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        outputStream.write(zipContent);
    }

}
