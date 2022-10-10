// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean;

/**
 * Wraps an {@link InterruptedException} with an unchecked exception.
 *
 * @author bjorncs
 */
public class UncheckedInterruptedException extends RuntimeException {

    public UncheckedInterruptedException(String message, InterruptedException cause, boolean restoreInterruptFlag) {
        super(message, cause);
        if (restoreInterruptFlag) Thread.currentThread().interrupt();
    }

    public UncheckedInterruptedException(InterruptedException cause, boolean restoreInterruptFlags) {
        this(cause.toString(), cause, restoreInterruptFlags);
    }

    public UncheckedInterruptedException(String message, boolean restoreInterruptFlag) { this(message, null, restoreInterruptFlag); }

    public UncheckedInterruptedException(String message, InterruptedException cause) { this(message, cause, false); }

    public UncheckedInterruptedException(InterruptedException cause) { this(cause.toString(), cause, false); }

    @Override public InterruptedException getCause() { return (InterruptedException) super.getCause(); }
}
