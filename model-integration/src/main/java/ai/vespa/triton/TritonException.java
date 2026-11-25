// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import com.yahoo.api.annotations.Beta;

/**
 * A failure of a request to Triton Inference Server, other than a timeout.
 *
 * @author bjorncs
 * @author glebashnik
 */
@Beta
public class TritonException extends RuntimeException {

    public TritonException(String message) {
        super(message);
    }

    public TritonException(String message, Throwable cause) {
        super(message, cause);
    }

}
