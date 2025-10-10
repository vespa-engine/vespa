// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

/**
 * Strategy for limiting how many file handles we can open.
 *
 * @author johsol
 */
public interface FileHandleBudget {
    long maxReadersAllowed();
}
