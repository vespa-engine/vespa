// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.flags;

import com.yahoo.jdisc.Response;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.http.StaticResponse;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hakonhall
 */
public class V1Response extends StaticResponse {
    public V1Response(String flagsV1Uri) {
        super(Response.Status.OK, HttpConfigResponse.JSON_CONTENT_TYPE, generateBody(flagsV1Uri));
    }

    private static String generateBody(String flagsV1Uri) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor data = root.setObject("data");
        data.setString("url", flagsV1Uri + "/data");
        return Utf8.toString(uncheck(() -> SlimeUtils.toJsonBytes(slime)));
    }
}
