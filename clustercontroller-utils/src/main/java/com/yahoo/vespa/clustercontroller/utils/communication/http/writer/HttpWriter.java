// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http.writer;

public class HttpWriter {

    private final StringBuilder builder = new StringBuilder();

    private String title = "Untitled page";
    enum State { HEADER, BODY, FINALIZED }

    private State state = State.HEADER;

    public HttpWriter() {
    }

    public HttpWriter addTitle(String title) {
        verifyState(State.HEADER);
        this.title = title;
        return this;
    }

    public HttpWriter write(String paragraph) {
        verifyState(State.BODY);
        builder.append("    <p>\n")
               .append("      " + paragraph + "\n")
               .append("    </p>\n");
        return this;
    }

    public HttpWriter writeLink(String name, String link) {
        verifyState(State.BODY);
        builder.append("    <a href=\"" + link + "\">" + name + "</a>\n");
        return this;
    }

    private void verifyState(State state) {
        if (this.state == state) return;
        if (state != State.FINALIZED && this.state == State.FINALIZED) {
            throw new IllegalStateException("HTTP page already finalized");
        }
        if (state == State.HEADER && this.state == State.BODY) {
            throw new IllegalStateException("Have already started to write body. Cannot alter header");
        }
        if (this.state == State.HEADER) {
            builder.append("<html>\n"
                    + "  <head>\n"
                    + "    <title>" + title + "</title>\n"
                    + "  </head>\n"
                    + "  <body>\n"
                    + "    <h1>" + title + "</h1>\n");
            this.state = State.BODY;
            if (this.state == state) return;
        }
            // If we get here we are in state body and want to get finalized
        builder.append("  </body>\n"
                     + "</html>\n");
        this.state = State.FINALIZED;
    }

    public String toString() {
        verifyState(State.FINALIZED);
        return builder.toString();
    }

}
