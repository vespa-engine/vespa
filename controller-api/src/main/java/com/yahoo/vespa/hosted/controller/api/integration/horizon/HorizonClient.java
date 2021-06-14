// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.horizon;

/**
 * @author olaa
 */
public interface HorizonClient {

    HorizonResponse getMetrics(byte[] query);

    HorizonResponse getUser();

    HorizonResponse getDashboard(String dashboardId);

    HorizonResponse getFavorite(String userId);

    HorizonResponse getTopFolders();

    HorizonResponse getRecent(String userId);

    HorizonResponse getClipboard(String dashboardId);

    HorizonResponse getMetaData(byte[] query);

}
