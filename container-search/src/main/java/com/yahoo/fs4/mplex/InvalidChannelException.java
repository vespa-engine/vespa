// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// -*- mode: java; folded-file: t; c-basic-offset: 4 -*-

package com.yahoo.fs4.mplex;

/**
 * @author <a href="mailto:borud@yahoo-inc.com">Bj\u00f8rn Borud</a>
 */
@SuppressWarnings("serial")
public class InvalidChannelException extends Exception
{
    public InvalidChannelException (String message) {
        super(message);
    }
}
