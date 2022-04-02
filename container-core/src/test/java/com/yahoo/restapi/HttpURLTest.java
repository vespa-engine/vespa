// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
class HttpURLTest {

    @Test
    void testConversion() {
        for (String uri : List.of("http://minimal",
                                  "http://empty.query?",
                                  "http://zero-port:0?no=path",
                                  "http://only-path/",
                                  "https://strange/queries?=&foo",
                                  "https://weirdness?=foo",
                                  "https://encoded/%3F%3D%26%2F?%3F%3D%26%2F=%3F%3D%26%2F",
                                  "https://host.at.domain:123/one/two/?three=four&five"))
            assertEquals(uri, HttpURL.from(URI.create(uri)).asURI().toString(),
                         "uri '" + uri + "' should be returned unchanged");
    }

}
