// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

/**
 * An AdminComponent cannot assume anything about the environment until enable()
 * is called: Required YUM packages may not have been installed, services
 * not started, etc. An enabled AdminComponent can be disabled to disengage from
 * the environment.
 */
public interface AdminComponent {
    /**
     * Enable component. May be called more than once.
     */
    void enable();

    /**
     * Disable component. May be called more than once.
     * Must be compatible with component deconstruct().
     */
    void disable();
}
