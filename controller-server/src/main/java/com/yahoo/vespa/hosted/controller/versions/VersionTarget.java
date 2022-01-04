// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;

/**
 * Interface for a version target of some kind of upgrade.
 *
 * @author mpolden
 */
public interface VersionTarget {

    /** The version of this target */
    Version version();

    /** Returns whether this target is potentially a downgrade */
    boolean downgrade();

}
