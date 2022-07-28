// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class ResponseTestCase {

    @Test
    void requireThatAccessorsWork() {
        Response response = new Response(69);
        assertEquals(69, response.getStatus());
        response.setStatus(96);
        assertEquals(96, response.getStatus());
        Throwable t = new Throwable();
        response.setError(t);
        assertSame(t, response.getError());
        assertTrue(response.context().isEmpty());
        assertTrue(response.headers().isEmpty());
    }

    @Test
    void requireThatStatusCodesDoNotChange() {
        assertEquals(100, Response.Status.CONTINUE);
        assertEquals(101, Response.Status.SWITCHING_PROTOCOLS);
        assertEquals(102, Response.Status.PROCESSING);

        assertEquals(200, Response.Status.OK);
        assertEquals(201, Response.Status.CREATED);
        assertEquals(202, Response.Status.ACCEPTED);
        assertEquals(203, Response.Status.NON_AUTHORITATIVE_INFORMATION);
        assertEquals(204, Response.Status.NO_CONTENT);
        assertEquals(205, Response.Status.RESET_CONTENT);
        assertEquals(206, Response.Status.PARTIAL_CONTENT);
        assertEquals(207, Response.Status.MULTI_STATUS);

        assertEquals(300, Response.Status.MULTIPLE_CHOICES);
        assertEquals(301, Response.Status.MOVED_PERMANENTLY);
        assertEquals(302, Response.Status.FOUND);
        assertEquals(303, Response.Status.SEE_OTHER);
        assertEquals(304, Response.Status.NOT_MODIFIED);
        assertEquals(305, Response.Status.USE_PROXY);
        assertEquals(307, Response.Status.TEMPORARY_REDIRECT);

        assertEquals(400, Response.Status.BAD_REQUEST);
        assertEquals(401, Response.Status.UNAUTHORIZED);
        assertEquals(402, Response.Status.PAYMENT_REQUIRED);
        assertEquals(403, Response.Status.FORBIDDEN);
        assertEquals(404, Response.Status.NOT_FOUND);
        assertEquals(405, Response.Status.METHOD_NOT_ALLOWED);
        assertEquals(406, Response.Status.NOT_ACCEPTABLE);
        assertEquals(407, Response.Status.PROXY_AUTHENTICATION_REQUIRED);
        assertEquals(408, Response.Status.REQUEST_TIMEOUT);
        assertEquals(409, Response.Status.CONFLICT);
        assertEquals(410, Response.Status.GONE);
        assertEquals(411, Response.Status.LENGTH_REQUIRED);
        assertEquals(412, Response.Status.PRECONDITION_FAILED);
        assertEquals(413, Response.Status.REQUEST_TOO_LONG);
        assertEquals(414, Response.Status.REQUEST_URI_TOO_LONG);
        assertEquals(415, Response.Status.UNSUPPORTED_MEDIA_TYPE);
        assertEquals(416, Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertEquals(417, Response.Status.EXPECTATION_FAILED);
        assertEquals(419, Response.Status.INSUFFICIENT_SPACE_ON_RESOURCE);
        assertEquals(420, Response.Status.METHOD_FAILURE);
        assertEquals(422, Response.Status.UNPROCESSABLE_ENTITY);
        assertEquals(423, Response.Status.LOCKED);
        assertEquals(424, Response.Status.FAILED_DEPENDENCY);

        assertEquals(505, Response.Status.VERSION_NOT_SUPPORTED);
        assertEquals(500, Response.Status.INTERNAL_SERVER_ERROR);
        assertEquals(501, Response.Status.NOT_IMPLEMENTED);
        assertEquals(502, Response.Status.BAD_GATEWAY);
        assertEquals(503, Response.Status.SERVICE_UNAVAILABLE);
        assertEquals(504, Response.Status.GATEWAY_TIMEOUT);
        assertEquals(505, Response.Status.VERSION_NOT_SUPPORTED);
        assertEquals(507, Response.Status.INSUFFICIENT_STORAGE);
    }

}
