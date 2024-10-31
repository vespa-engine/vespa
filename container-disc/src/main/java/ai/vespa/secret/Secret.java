// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.secret;

/**
 * @author lesters
 */
public interface Secret {

    /** Returns the current value of this secret */
    String current();

}
