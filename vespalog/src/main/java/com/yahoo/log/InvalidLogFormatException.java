// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

/**
 * This (checked) exception is used to flag invalid log messages,
 * primarily for use in the factory methods of LogMessage.
 *
 * @author  Bjorn Borud
 */
public class InvalidLogFormatException extends Exception
{
    public InvalidLogFormatException (String msg) {
        super(msg);
    }

    public InvalidLogFormatException (String msg, Throwable cause) {
        super(msg, cause);
    }

    public InvalidLogFormatException () {
    }
}
