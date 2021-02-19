// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.execution;

import com.yahoo.processing.Response;

/**
 * An interface for classes which can be given responses.
 * Freeze listeners may implement this to be handed the response
 * before they are run. There is probably no other sensible use for this.
 *
 * @author bratseth
 */
public interface ResponseReceiver {

    public void setResponse(Response response);

}
