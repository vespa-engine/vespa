// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.noderepository.bindings;

/**
 * Automagically handles (de)serialization based on 1:1 message fields and identifier names.
 * Deserializes JSON strings on the form:
 * <pre>
 *   {
 *     "message": "Updated host.com"
 *   }
 * </pre>
 *
 * @author bakksjo
 */
public class UpdateNodeAttributesResponse {
    public String message;
}
