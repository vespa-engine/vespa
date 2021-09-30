// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.response;

import com.yahoo.component.provider.ListenableFreezable;
import com.yahoo.processing.Request;

/**
 * A data item created due to a processing request.
 * <p>
 * If a data item is <i>frozen</i> it is illegal to make further changes to its payload or referenced request.
 *
 * @author bratseth
 */
// TODO: Have DataList implement this instead, probably (should be a safe change in practise)
public interface Data extends ListenableFreezable {

    /** Returns the request that created this data */
    Request request();

}
