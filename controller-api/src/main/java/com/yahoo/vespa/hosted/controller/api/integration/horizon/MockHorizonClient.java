// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.horizon;

/**
 * @author olaa
 */
public class MockHorizonClient implements HorizonClient {

    @Override
    public HorizonResponse getMetrics(byte[] query) {
        return HorizonResponse.empty();
    }

    @Override
    public HorizonResponse getUser() {
        return HorizonResponse.empty();
    }

    @Override
    public HorizonResponse getDashboard(int dashboardId) {
        return HorizonResponse.empty();
    }

    @Override
    public HorizonResponse getTopFolders() {
        return HorizonResponse.empty();
    }

    @Override
    public HorizonResponse getMetaData(byte[] query) {
        return HorizonResponse.empty();
    }

}
