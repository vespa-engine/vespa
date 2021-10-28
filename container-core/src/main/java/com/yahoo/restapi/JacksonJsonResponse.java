// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A JSON response using Jackson for serialization.
 *
 * @author bjorncs
 */
public class JacksonJsonResponse<T> extends HttpResponse {

    private static final Logger log = Logger.getLogger(JacksonJsonResponse.class.getName());

    private final ObjectMapper jsonMapper;
    private final boolean prettyPrint;
    private final T entity;

    public JacksonJsonResponse(int statusCode, T entity) {
        this(statusCode, entity, false);
    }

    public JacksonJsonResponse(int statusCode, T entity, boolean prettyPrint) {
        this(statusCode, entity, JacksonJsonMapper.instance, prettyPrint);
    }

    public JacksonJsonResponse(int statusCode, T entity, ObjectMapper jsonMapper) {
        this(statusCode, entity, jsonMapper, false);
    }

    public JacksonJsonResponse(int statusCode, T entity, ObjectMapper jsonMapper, boolean prettyPrint) {
        super(statusCode);
        this.entity = entity;
        this.jsonMapper = jsonMapper;
        this.prettyPrint = prettyPrint;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        ObjectWriter writer = prettyPrint ? jsonMapper.writerWithDefaultPrettyPrinter() : jsonMapper.writer();
        if (log.isLoggable(Level.FINE)) {
            String json = writer.writeValueAsString(entity);
            log.log(Level.FINE, "Writing the following JSON to response output stream:\n" + json);
            outputStream.write(json.getBytes());
        } else {
            writer.writeValue(outputStream, entity);
        }
    }

    @Override public String getContentType() { return "application/json"; }
    public T getEntity() { return entity; }

}
