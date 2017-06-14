// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

/**
 * Represents an action to restart services in order to handle a config change.
 *
 * @author geirst
 * @since 5.43
 */
public interface ConfigChangeRestartAction extends ConfigChangeAction {

    @Override
    default Type getType() { return Type.RESTART; }

    /** Restarts are handled automatically so they are allowed */
    @Override
    default boolean allowed() { return true; }

}
