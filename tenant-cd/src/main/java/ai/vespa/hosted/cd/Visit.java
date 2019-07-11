package ai.vespa.hosted.cd;

import java.util.Map;

/**
 * A stateful visit operation against a {@link Endpoint}.
 *
 * @author jonmv
 */
public class Visit {

    // Delegate to a blocking iterator, which can be used for iteration as visit is ongoing.
    public Map<DocumentId, Document> documents() {
        return null;
    }

}
