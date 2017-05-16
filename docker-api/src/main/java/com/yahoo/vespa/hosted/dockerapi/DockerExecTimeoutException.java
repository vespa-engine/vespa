package com.yahoo.vespa.hosted.dockerapi;

/**
 * Runtime exception to be thrown when the exec commands did not finish in time.
 *
 * The underlying process has not been killed. If you need the process to be
 * killed you need to wrap it into a commands that times out.
 *
 * @author smorgrav
 */
@SuppressWarnings("serial")
public class DockerExecTimeoutException extends RuntimeException {
    public DockerExecTimeoutException(String msg) {
        super(msg);
    }
}
