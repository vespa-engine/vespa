package com.yahoo.vespa.config.proxy.filedistribution;

import java.util.Optional;

/**
 * Parameters for downloading files.
 *
 * @author onur
 */
public class DownloadOptions {

    private final String authToken;

    public DownloadOptions(String authToken) {
        this.authToken = authToken;
    }

    public Optional<String> getAuthToken() {
        return Optional.ofNullable(authToken);
    }
}
