package com.yahoo.vespa.hosted.node.admin.maintenance.coredump;

/**
 * @author valerijf
 */
public interface CoredumpReporter {

    /** Report a coredump with a given ID and given metadata */
    void reportCoredump(String id, String metadata);
}
