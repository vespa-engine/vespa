package ai.vespa.hosted.cd;

import java.util.Map;

/**
 * The immutable result of sending a {@link Query} to a Vespa {@link Endpoint}.
 *
 * @author jonmv
 */
public class Search {

    private final String raw;

    public Search(String raw) {
        this.raw = raw;
    }

    public String rawOutput() { return raw; }

    // hits
    // coverage
    // searched
    // full?
    // results?
    // resultsFull?

    /** Returns the documents that were returned as the result, with iteration order as returned. */
    Map<DocumentId, Document> documents() {
        return null;
    }

}
