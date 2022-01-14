// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import java.util.concurrent.TimeoutException;

/**
 * Unchecked alternative for {@link java.util.concurrent.TimeoutException}.
 *
 * @author bjorncs
 */
public class UncheckedTimeoutException extends RuntimeException {

    public UncheckedTimeoutException() {}

    public UncheckedTimeoutException(TimeoutException cause) { super(cause.getMessage(), cause); }

    public UncheckedTimeoutException(String message) { super(message); }

    public UncheckedTimeoutException(Throwable cause) { super(cause); }

    public UncheckedTimeoutException(String message, Throwable cause) { super(message, cause); }

}
