// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

/**
 * Represents the health status of a global rotation.
 *
 * @author mpolden
 */
public enum RotationStatus {

    /** Rotation has status 'in' and is receiving traffic */
    in,

    /** Rotation has status 'out' and is *NOT* receiving traffic */
    out,

    /** Rotation status is currently unknown, or no global rotation has been assigned */
    unknown

}
