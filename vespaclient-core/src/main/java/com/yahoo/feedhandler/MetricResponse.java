// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.metrics.MetricSet;
import com.yahoo.text.XMLWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Response that generates metric output like a status page.
 */
public final class MetricResponse extends HttpResponse {

    MetricSet set;

    MetricResponse(MetricSet set) {
        super(com.yahoo.jdisc.http.HttpResponse.Status.OK);
        this.set = set;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        XMLWriter writer = new XMLWriter(new OutputStreamWriter(stream));
        writer.openTag("status");
        set.printXml(writer, 0, 2);
        writer.closeTag();
        writer.flush();
    }

    @Override
    public java.lang.String getContentType() {
        return "application/xml";
    }

}
