// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ClusterSpec;

import java.util.List;
import java.util.Optional;

/**
 * Contains the action to be performed on the given services to handle a config change
 * between the current active model and the next model to prepare.
 *
 * @author geirst
 */
public interface ConfigChangeAction {

    enum Type {
        RESTART("restart"), REFEED("refeed"), REINDEX("reindex");

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

    /** When this is non-empty, validation may fail unless this validation id is allowed by validation overrides. */
    default Optional<ValidationId> validationId() { return Optional.empty(); }

    /** The id of the cluster that needs this action applied */
    ClusterSpec.Id clusterId();

    /** Returns whether this change should be ignored for internal redeploy */
    default boolean ignoreForInternalRedeploy() {
        return false;
    };

}
