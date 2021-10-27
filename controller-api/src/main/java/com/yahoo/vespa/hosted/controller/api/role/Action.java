// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.jdisc.http.HttpRequest;

import java.util.EnumSet;

/**
 * Action defines an operation, typically a HTTP method, that may be performed on an entity in the controller
 * (e.g. tenant or application).
 *
 * @author mpolden
 */
public enum Action {

    create,
    read,
    update,
    delete;

    static EnumSet<Action> all() {
        return EnumSet.allOf(Action.class);
    }

    static EnumSet<Action> write() {
        return EnumSet.of(create, update, delete);
    }

    /** Returns the appropriate action for given HTTP method */
    public static Action from(HttpRequest.Method method) {
        switch (method) {
            case POST: return Action.create;
            case GET:
            case OPTIONS:
            case HEAD: return Action.read;
            case PUT:
            case PATCH: return Action.update;
            case DELETE: return Action.delete;
            default: throw new IllegalArgumentException("No action defined for method " + method);
        }
    }

}
