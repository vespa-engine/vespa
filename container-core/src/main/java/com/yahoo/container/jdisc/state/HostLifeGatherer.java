// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.yahoo.vespa.defaults.Defaults;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * @author olaa
 */
public class HostLifeGatherer {

    private static final Path UPTIME_PATH = Path.of(Defaults.getDefaults().underVespaHome("/proc"));

    protected static JSONObject getHostLifePacket() {
        long upTime;
        int statusCode = 0;
        String statusMessage = "OK";

        try {
            upTime = Files.getLastModifiedTime(UPTIME_PATH).toInstant().getEpochSecond();
        } catch (IOException e) {
            upTime = 0;
            statusCode = 1;
            statusMessage = "Unable to read proc folder";
        }


        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("status_code", statusCode);
            jsonObject.put("status_msg", statusMessage);
            jsonObject.put("timestamp", Instant.now().getEpochSecond());
            jsonObject.put("application", "host_life");
            JSONObject metrics = new JSONObject();
            metrics.put("uptime", upTime);
            metrics.put("alive", 1);
            jsonObject.put("metrics", metrics);

        } catch (JSONException e) {}

        return jsonObject;
    }
}
