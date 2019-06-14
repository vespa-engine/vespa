package ai.vespa.hosted.cd;

import java.util.Map;
import java.util.Set;

/**
 * An immutable set of document feed / update / delete operations, which can be sent to a Vespa {@link TestEndpoint}.
 *
 * @author jonmv
 */
public class Feed {

    Map<DocumentId, Document> creations() {
        return null;
    }

    Map<DocumentId, Document> updates() {
        return null;
    }

    Set<DocumentId> deletions() {
        return null;
    }

}
