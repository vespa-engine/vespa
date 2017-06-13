// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.List;

/**
 * Contains the action to be performed on the given services to handle a config change
 * between the current active model and the next model to prepare.
 *
 * @author geirst
 * @since 5.40
 */
public interface ConfigChangeAction {

    enum Type {
        RESTART("restart"), REFEED("refeed");

        private final String type;

        Type(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return type;
        }
    }

    /** Returns what type of action is required to handle this config change */
    Type getType();

    /** Returns a message describing the config change in detail */
    String getMessage();

    /** Returns the list of services where the action must be performed */
    List<ServiceInfo> getServices();

    /** Returns whether this change should be allowed */
    boolean allowed();

}
