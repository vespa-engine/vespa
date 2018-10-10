// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

/**
 * An enum of the stemming modes which can be requested.
 * Stemming implementation may support a smaller number of modes by mapping a mode to a more
 * inclusive alternative.
 *
 * @author Mathias Mølster Lidal
 */
public enum StemMode {

    NONE(0),
    DEFAULT(1),
    ALL(2),
    SHORTEST(4),
    BEST(5);

    private final int value;

    StemMode(int value) {
        this.value = value;
    }

}
