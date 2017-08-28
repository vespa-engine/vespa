// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.nonpublic;

/**
 * Non public header fields that are not part of the public api.
 *
 * Placed here since this is the only module we own that both the
 * command-line client and controller-server depend on.
 *
 * @author Tony Vaagenes
 */
public class HeaderFields {
    public static final String USER_ID_HEADER_FIELD = "vespa.hosted.trusted.username";
}
