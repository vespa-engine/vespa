package com.yahoo.vespa.hosted.node.admin.provider;

import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;

/**
 * Class for setting up instances of classes; enables testing.
 *
 * @author dybis
 */
public interface ComponentsProvider {
     NodeAdmin.NodeAdminStateUpdater getNodeAdminStateUpdater();

}
