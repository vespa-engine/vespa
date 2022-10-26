// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool;

/**
 * A named tool that can be invoked via the parent Main program.
 *
 * @author vekterli
 */
public interface Tool {

    /**
     * Name of the tool used verbatim on the command line.
     */
    String name();

    /**
     * Description used when "--help" is invoked for a particular tool
     */
    ToolDescription description();

    /**
     * Invokes the tool logic with a ToolInvocation that encapsulates the command line
     * and input/ouput environment the tool was called in.
     *
     * @param invocation parameters and environment to be used by the tool
     * @return exit code that will be returned by the main process
     */
    int invoke(ToolInvocation invocation);

}
