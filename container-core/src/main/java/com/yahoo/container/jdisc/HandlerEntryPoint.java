package com.yahoo.container.jdisc;

import java.net.URI;

public class HandlerEntryPoint {
    private final URI path;

    private HandlerEntryPoint(URI path) {
        this.path = path;
    }

    public URI path() {
        return path;
    }

    public static HandlerEntryPoint of(String path) {
        return new HandlerEntryPoint(URI.create(path));
    }
}
