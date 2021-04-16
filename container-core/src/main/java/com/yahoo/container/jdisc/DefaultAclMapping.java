// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.jdisc;

/**
 * Default ACL mapping
 * @author mortent
 */
public class DefaultAclMapping implements AclMapping {

    @Override
    public Action get(RequestView requestMeta) {
        switch (requestMeta.method()) {
            case GET:
            case HEAD:
            case OPTIONS:
                return Action.READ;
            case POST:
            case DELETE:
            case PUT:
            case PATCH:
            case CONNECT:
            case TRACE:
                return Action.WRITE;
            default:
                throw new IllegalArgumentException("Illegal request method: " + requestMeta.method());
        }
    }
}
