// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.jdisc.SharedResource;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Result;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public interface ClientSession extends SharedResource {

    public Result sendMessage(Message msg);
}
