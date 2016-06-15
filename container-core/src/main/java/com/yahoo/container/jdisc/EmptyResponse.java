// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Placeholder response when no content, only headers and status is to be
 * returned.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class EmptyResponse extends HttpResponse {

    public EmptyResponse(int status) {
        super(status);
    }

    public void render(OutputStream outputStream) throws IOException {
        // NOP
    }
}
