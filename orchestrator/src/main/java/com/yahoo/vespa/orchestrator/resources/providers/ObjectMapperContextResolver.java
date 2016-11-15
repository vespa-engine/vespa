package com.yahoo.vespa.orchestrator.resources.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.scala.DefaultScalaModule$;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

/**
 * Allow Scala case classes to be serialized to JSON
 * @author bjorncs
 */
@Provider
public class ObjectMapperContextResolver implements ContextResolver<ObjectMapper> {
    private final ObjectMapper objectMapper;

    public ObjectMapperContextResolver() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(DefaultScalaModule$.MODULE$);
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return objectMapper;
    }
}
