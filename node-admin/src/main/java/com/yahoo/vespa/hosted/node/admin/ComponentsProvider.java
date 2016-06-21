package com.yahoo.vespa.hosted.node.admin;

/**
 * Class for setting up instances of classes; enables testing.
 *
 * @author dybis
 */
public interface ComponentsProvider {
     NodeAdminStateUpdater getNodeAdminStateUpdater();

}
