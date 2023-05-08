// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status.statuspage;

import com.google.common.html.HtmlEscapers;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class StatusPageResponse {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private String contentType;
    private ResponseCode responseCode = ResponseCode.OK;

    public enum ResponseCode {
        OK(200, "OK"),
        NOT_MODIFIED(304, "Not Modified"),
        BAD_REQUEST(400, "Bad Request"),
        NOT_FOUND(404, "Not Found"),
        INTERNAL_SERVER_ERROR(500, "Internal Server Error");

        private final int code;
        private final String message;
        ResponseCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }

    public String getContentType() { return contentType; }
    public ResponseCode getResponseCode() { return responseCode; }
    public ByteArrayOutputStream getOutputStream() { return output; }

    public BufferedWriter createBufferedWriter() {
        return new BufferedWriter(new OutputStreamWriter(output));
    }

    public void writeContent(String content) {
        try {
            BufferedWriter writer = createBufferedWriter();
            writer.write(content);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setContentType(String type) { contentType = type; }
    public void setResponseCode(ResponseCode responseCode) {
        this.responseCode = responseCode;
    }

    public void writeHtmlHeader(StringBuilder content, String title) {
        String escaped_title = HtmlEscapers.htmlEscaper().escape(title);
        content.append("<html>\n")
               .append("<head><title>").append(escaped_title).append("</title></head>")
               .append("<body>\n")
               .append("<h1>").append(escaped_title).append("</h1>\n");
    }

    public void writeHtmlFooter(StringBuilder content, String hiddenMessage) {
        if (hiddenMessage != null && !hiddenMessage.isEmpty()) {
            content.append("\n<!-- " + HtmlEscapers.htmlEscaper().escape(hiddenMessage) + " -->\n");
        }
        content.append("</body>\n")
               .append("</html>\n");
    }

}
