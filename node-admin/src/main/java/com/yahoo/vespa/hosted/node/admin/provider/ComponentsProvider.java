package com.yahoo.vespa.hosted.node.admin.provider;

import com.yahoo.vespa.hosted.node.admin.NodeAdminStateUpdater;

/**
 * Class for setting up instances of classes; enables testing.
 *
 * @author dybis
 */
public interface ComponentsProvider {
     NodeAdminStateUpdater getNodeAdminStateUpdater();

}
