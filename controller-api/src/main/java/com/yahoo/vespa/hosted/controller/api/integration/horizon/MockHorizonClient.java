// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.horizon;

import java.io.InputStream;

/**
 * @author olaa
 */
public class MockHorizonClient implements HorizonClient {

    @Override
    public InputStream getMetrics(byte[] query) {
        return null;
    }

    @Override
    public InputStream getUser() {
        return null;
    }

    @Override
    public InputStream getDashboard(String dashboardId) {
        return null;
    }

    @Override
    public InputStream getFavorite(String userId) {
        return null;
    }

    @Override
    public InputStream getTopFolders() {
        return null;
    }

    @Override
    public InputStream getRecent(String userId) {
        return null;
    }

    @Override
    public InputStream getClipboard(String dashboardId) {
        return null;
    }

    @Override
    public InputStream getMetaData(byte[] query) {
        return null;
    }
}
