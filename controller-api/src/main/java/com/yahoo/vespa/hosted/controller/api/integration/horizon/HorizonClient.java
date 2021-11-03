// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.horizon;

/**
 * @author olaa
 */
public interface HorizonClient {

    HorizonResponse getMetrics(byte[] query);

    HorizonResponse getUser();

    HorizonResponse getDashboard(int dashboardId);

    HorizonResponse getTopFolders();

    HorizonResponse getMetaData(byte[] query);

}
