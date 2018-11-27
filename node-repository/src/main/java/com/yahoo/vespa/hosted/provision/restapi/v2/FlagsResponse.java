// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.provision.flag.Flag;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * @author mpolden
 */
public class FlagsResponse extends HttpResponse {

    private final List<Flag> flags;

    public FlagsResponse(List<Flag> flags) {
        super(200);
        this.flags = flags;
    }

    @Override
    public void render(OutputStream out) throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor flagArray = root.setArray("flags");
        flags.forEach(flag -> {
            Cursor flagObject = flagArray.addObject();
            flagObject.setString("id", flag.id().serializedValue());
            flagObject.setBool("enabled", flag.isEnabled());
            Cursor nodeArray = flagObject.setArray("enabledHostnames");
            flag.hostnames().forEach(nodeArray::addString);
            Cursor applicationArray = flagObject.setArray("enabledApplications");
            flag.applications().stream()
                .map(ApplicationId::serializedForm)
                .forEach(applicationArray::addString);
        });
        new JsonFormat(true).encode(out, slime);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

}
