// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.RestApi.ExceptionMapper;
import com.yahoo.restapi.RestApi.RequestMapper;
import com.yahoo.restapi.RestApi.ResponseMapper;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Implementations of {@link ExceptionMapper}, {@link RequestMapper} and {@link ResponseMapper}.
 *
 * @author bjorncs
 */
public class RestApiMappers {

    private static final Logger log = Logger.getLogger(RestApiMappers.class.getName());

    static List<RequestMapperHolder<?>> DEFAULT_REQUEST_MAPPERS = List.of(
            new RequestMapperHolder<>(Slime.class, RestApiMappers::toSlime),
            new RequestMapperHolder<>(JsonNode.class, ctx -> toJsonNode(ctx, ctx.jacksonJsonMapper())),
            new RequestMapperHolder<>(String.class, RestApiMappers::toString),
            new RequestMapperHolder<>(byte[].class, RestApiMappers::toByteArray),
            new RequestMapperHolder<>(InputStream .class, RestApiMappers::toInputStream),
            new RequestMapperHolder<>(Void.class, ctx -> Optional.empty()));

    static List<ResponseMapperHolder<?>> DEFAULT_RESPONSE_MAPPERS = List.of(
            new ResponseMapperHolder<>(HttpResponse.class, (context, entity) -> entity),
            new ResponseMapperHolder<>(String.class, (context, entity) -> new MessageResponse(entity)),
            new ResponseMapperHolder<>(Slime.class, (context, entity) -> new SlimeJsonResponse(entity)),
            new ResponseMapperHolder<>(JsonNode.class,
                    (context, entity) -> new JacksonJsonResponse<>(200, entity, context.jacksonJsonMapper(), true)));

    static List<ExceptionMapperHolder<?>> DEFAULT_EXCEPTION_MAPPERS = List.of(
            new ExceptionMapperHolder<>(RestApiException.class, (context, exception) -> exception.response()));

    private RestApiMappers() {}

    public static class JacksonRequestMapper<ENTITY> implements RequestMapper<ENTITY> {
        private final Class<ENTITY> type;

        JacksonRequestMapper(Class<ENTITY> type) { this.type = type; }

        @Override
        public Optional<ENTITY> toRequestEntity(RestApi.RequestContext context) throws RestApiException {
            if (log.isLoggable(Level.FINE)) {
                return RestApiMappers.toString(context).map(string -> {
                    log.fine(() -> "Request content: " + string);
                    return convertIoException("Failed to parse JSON", () -> context.jacksonJsonMapper().readValue(string, type));
                });
            } else {
                return toInputStream(context)
                        .map(in -> convertIoException("Invalid JSON", () -> context.jacksonJsonMapper().readValue(in, type)));
            }
        }
    }

    public static class JacksonResponseMapper<ENTITY> implements ResponseMapper<ENTITY> {
        @Override
        public HttpResponse toHttpResponse(RestApi.RequestContext context, ENTITY responseEntity) throws RestApiException {
            return new JacksonJsonResponse<>(200, responseEntity, context.jacksonJsonMapper(), true);
        }
    }

    static class RequestMapperHolder<ENTITY> {
        final Class<ENTITY> type;
        final RestApi.RequestMapper<ENTITY> mapper;

        RequestMapperHolder(Class<ENTITY> type, RequestMapper<ENTITY> mapper) {
            this.type = type;
            this.mapper = mapper;
        }
    }

    static class ResponseMapperHolder<ENTITY> {
        final Class<ENTITY> type;
        final RestApi.ResponseMapper<ENTITY> mapper;

        ResponseMapperHolder(Class<ENTITY> type, RestApi.ResponseMapper<ENTITY> mapper) {
            this.type = type;
            this.mapper = mapper;
        }

        HttpResponse toHttpResponse(RestApi.RequestContext ctx, Object entity) {
            return mapper.toHttpResponse(ctx, type.cast(entity));
        }
    }

    static class ExceptionMapperHolder<EXCEPTION extends RuntimeException> {
        final Class<EXCEPTION> type;
        final RestApi.ExceptionMapper<EXCEPTION> mapper;

        ExceptionMapperHolder(Class<EXCEPTION> type, RestApi.ExceptionMapper<EXCEPTION> mapper) {
            this.type = type;
            this.mapper = mapper;
        }

        HttpResponse toResponse(RestApi.RequestContext ctx, RuntimeException e) {
            return mapper.toResponse(ctx, type.cast(e));
        }
    }

    private static Optional<InputStream> toInputStream(RestApi.RequestContext context) {
        return context.requestContent().map(RestApi.RequestContext.RequestContent::content);
    }

    private static Optional<byte[]> toByteArray(RestApi.RequestContext context) {
        InputStream in = toInputStream(context).orElse(null);
        if (in == null) return Optional.empty();
        return convertIoException(() -> Optional.of(in.readAllBytes()));
    }

    private static Optional<String> toString(RestApi.RequestContext context) {
        try {
            return toByteArray(context).map(bytes -> new String(bytes, UTF_8));
        } catch (RuntimeException e) {
            throw new RestApiException.BadRequest("Failed parse request content as UTF-8: " + Exceptions.toMessageString(e), e);
        }
    }

    private static Optional<JsonNode> toJsonNode(RestApi.RequestContext context, ObjectMapper jacksonJsonMapper) {
        if (log.isLoggable(Level.FINE)) {
            return toString(context).map(string -> {
                log.fine(() -> "Request content: " + string);
                return convertIoException("Failed to parse JSON", () -> jacksonJsonMapper.readTree(string));
            });
        } else {
            return toInputStream(context)
                    .map(in -> convertIoException("Invalid JSON", () -> jacksonJsonMapper.readTree(in)));
        }
    }

    @FunctionalInterface private interface SupplierThrowingIoException<T> { T get() throws IOException; }
    private static <T> T convertIoException(String messagePrefix, SupplierThrowingIoException<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            log.log(Level.FINE, e.getMessage(), e);
            throw new RestApiException.InternalServerError(messagePrefix + ": " + Exceptions.toMessageString(e), e);
        }
    }

    private static <T> T convertIoException(SupplierThrowingIoException<T> supplier) {
        return convertIoException("Failed to read request content", supplier);
    }

    private static Optional<Slime> toSlime(RestApi.RequestContext context) {
        try {
            return toString(context).map(string -> {
                log.fine(() -> "Request content: " + string);
                return SlimeUtils.jsonToSlimeOrThrow(string);
            });
        } catch (com.yahoo.slime.JsonParseException e) {
            log.log(Level.FINE, e.getMessage(), e);
            throw new RestApiException.BadRequest("Invalid JSON: " + Exceptions.toMessageString(e), e);
        }
    }

}
