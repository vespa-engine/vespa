// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.billing;

import com.yahoo.container.jdisc.HttpResponse;
import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

class CsvResponse extends HttpResponse {
    private final String[] header;
    private final List<Object[]> rows;

    CsvResponse(String[] header, List<Object[]> rows) {
        super(200);
        this.header = header;
        this.rows = rows;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        var writer = new OutputStreamWriter(outputStream);
        var printer = CSVFormat.DEFAULT.withRecordSeparator('\n').withHeader(this.header).print(writer);
        for (var row : this.rows) printer.printRecord(row);
        printer.flush();
    }

    @Override
    public String getContentType() {
        return "text/csv; encoding=utf-8";
    }
}
