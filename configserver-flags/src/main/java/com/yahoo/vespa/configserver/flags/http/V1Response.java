// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags.http;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * @author hakonhall
 */
public class V1Response extends HttpResponse {

    private final Slime slime;

    public V1Response(String flagsV1Uri, String... names) {
        super(Response.Status.OK);
        this.slime = generateBody(flagsV1Uri, List.of(names));
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    private static Slime generateBody(String flagsV1Uri, List<String> names) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        names.forEach(name -> {
            Cursor data = root.setObject(name);
            data.setString("url", flagsV1Uri + "/" + name);
        });
        return slime;
    }

}
