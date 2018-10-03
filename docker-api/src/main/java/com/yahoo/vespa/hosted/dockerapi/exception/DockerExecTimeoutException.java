// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.exception;

/**
 * Runtime exception to be thrown when the exec commands did not finish in time.
 *
 * The underlying process has not been killed. If you need the process to be
 * killed you need to wrap it into a commands that times out.
 *
 * @author smorgrav
 */
@SuppressWarnings("serial")
public class DockerExecTimeoutException extends DockerException {
    public DockerExecTimeoutException(String msg) {
        super(msg);
    }
}
