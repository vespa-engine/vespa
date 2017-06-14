// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// -*- mode: java; folded-file: t; c-basic-offset: 4 -*-
//

package com.yahoo.fs4;


/**
 * Signal that a timeout occurred in the Channel2 communiction
 *
 * @author  <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 *
 */
@SuppressWarnings("serial")
public class ChannelTimeoutException extends Exception
{
    public ChannelTimeoutException (String msg) {
        super(msg);
    }

    public ChannelTimeoutException () {
    }
}
