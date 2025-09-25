package com.yahoo.restapi;

import ai.vespa.json.Json;
import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class JsonResponse extends HttpResponse {

    private final Json json;
    private final boolean compact;

    public JsonResponse() {
        this(Json.Builder.newObject().build(), true);
    }

    public JsonResponse(Json json) {
        this(200, json, true);
    }

    public JsonResponse(Json json, boolean compact) {
        this(200, json, compact);
    }

    public JsonResponse(int statusCode, Json json) {
        this(statusCode, json, true);
    }

    public JsonResponse(int statusCode, Json json, boolean compact) {
        super(statusCode);
        this.json = json;
        this.compact = compact;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        stream.write(json.toJson(!compact).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getContentType() {
        return "application/json";
    }


}
