// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.horizon;

import com.yahoo.slime.Slime;
import java.io.InputStream;

/**
 * @author olaa
 */
public interface HorizonClient {

    InputStream getMetrics(Slime query);

    InputStream getUser();

    InputStream getDashboard(String dashboardId) ;

    InputStream getFavorite(String userId);

    InputStream getTopFolders();

    InputStream getRecent(String userId);

    InputStream getClipboard(String dashboardId);

}
