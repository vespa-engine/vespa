// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * Interface used to notify when a {@link Target} becomes
 * invalid. Listening is controlled with the {@link Target#addWatcher
 * Target.addWatcher} and {@link Target#removeWatcher
 * Target.removeWatcher} methods.
 **/
public interface TargetWatcher {

    /**
     * Invoked when a target becomes invalid.
     *
     * @param target the target that has become invalid.
     **/
    public void notifyTargetInvalid(Target target);
}
