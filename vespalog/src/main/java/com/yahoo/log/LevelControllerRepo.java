// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

/**
 * The level controller repository is an interface towards something that is able to provide level
 * controllers for a given component.
 *
 * @author Ulf Lilleengen
 * @since 5.1
 * Should only be used internally in the log library
 */
interface LevelControllerRepo {
    /**
     * Return the level controller for a given component.
     * @param component The component name string.
     * @return The LevelController corresponding to that component. Return null if not found.
     */
    LevelController getLevelController(String component);

    /**
     * Close down the level controller repository. Cleanup should be done here.
     */
    void close();
}
