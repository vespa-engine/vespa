// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

/**
 * <p>A packet for requesting a list of document summaries.
 * This packet can be encoded only.</p>
 *
 * @author bratseth
 */
public class GetDocSumsPacket {

    /** Session id key. Yep, putting this here is ugly as hell */
    public static final String sessionIdKey = "sessionId";

}
