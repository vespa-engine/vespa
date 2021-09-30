// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Placeholder response when no content, only headers and status is to be returned.
 *
 * @author Steinar Knutsen
 */
public class EmptyResponse extends HttpResponse {

    public EmptyResponse(int status) {
        super(status);
    }

    public void render(OutputStream outputStream) throws IOException {
        // NOP
    }

}
