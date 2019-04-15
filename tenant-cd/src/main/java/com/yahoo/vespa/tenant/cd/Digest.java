package com.yahoo.vespa.tenant.cd;

import java.util.Set;

/**
 * An immutable report of the outcome of a {@link Feed} sent to a {@link TestEndpoint}.
 *
 * @author jonmv
 */
public class Digest {

    public Set<DocumentId> created() {
        return null;
    }

    public Set<DocumentId> updated() {
        return null;
    }

    public Set<DocumentId> deleted() {
        return null;
    }

    public Set<DocumentId> failed() {
        return null;
    }

}
