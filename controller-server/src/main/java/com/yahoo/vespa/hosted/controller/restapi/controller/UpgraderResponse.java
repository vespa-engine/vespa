package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author mpolden
 */
public class UpgraderResponse extends HttpResponse {

    private final double upgradesPerMinute;

    public UpgraderResponse(double upgradesPerMinute) {
        super(200);
        this.upgradesPerMinute = upgradesPerMinute;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setDouble("upgradesPerMinute", upgradesPerMinute);
        new JsonFormat(true).encode(outputStream, slime);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }
}
