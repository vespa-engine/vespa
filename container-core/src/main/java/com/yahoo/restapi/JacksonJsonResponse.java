// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.container.jdisc.HttpResponse;
import java.util.logging.Level;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 * A JSON response using Jackson for serialization.
 *
 * @author bjorncs
 */
public class JacksonJsonResponse<T> extends HttpResponse {

    private static final Logger log = Logger.getLogger(JacksonJsonResponse.class.getName());
    private static final ObjectMapper defaultJsonMapper =
            new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new Jdk8Module());

    private final ObjectMapper jsonMapper;
    private final T entity;

    public JacksonJsonResponse(int statusCode, T entity) {
        this(statusCode, entity, defaultJsonMapper);
    }

    public JacksonJsonResponse(int statusCode, T entity, ObjectMapper jsonMapper) {
        super(statusCode);
        this.entity = entity;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        if (log.isLoggable(Level.FINE)) {
            String json = jsonMapper.writeValueAsString(entity);
            log.log(Level.FINE, "Writing the following JSON to response output stream:\n" + json);
            outputStream.write(json.getBytes());
        } else {
            jsonMapper.writeValue(outputStream, entity);
        }
    }

    @Override public String getContentType() { return "application/json"; }
    public T getEntity() { return entity; }

}
