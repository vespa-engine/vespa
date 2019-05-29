// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * @author hakonhall
 */
public enum SystemType {
    /** Denotes a system similar to SystemName.main, such as cd. */
    MAIN,

    /** Denotes a system similar to SystemName.Public, such as PublicCd. */
    PUBLIC;
}
