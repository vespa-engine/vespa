// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import java.util.Objects;
import java.util.Optional;

/**
 * A {@link BindingPattern} which is constructed directly from a user provided 'binding' element from services.xml.
 *
 * @author bjorncs
 */
public class UserBindingPattern extends BindingPattern {

    private final Optional<String> originalPort;

    private UserBindingPattern(String scheme, String host, String port, String path) {
        super(scheme, host, port, path);
        this.originalPort = null;
    }
    private UserBindingPattern(String scheme, String host, String port, Optional<String> originalPort, String path) {
        super(scheme, host, port, path);
        this.originalPort = originalPort;
    }
    private UserBindingPattern(String binding) {
        super(binding);
        this.originalPort = null;
    }

    public static UserBindingPattern fromHttpPath(String path) { return new UserBindingPattern("http", "*", null, path); }
    public static UserBindingPattern fromPattern(String binding) { return new UserBindingPattern(binding); }
    public UserBindingPattern withOverriddenPort(int port) { return new UserBindingPattern(scheme(), host(), Integer.toString(port), port(), path()); }

    public Optional<String> originalPort() {
        return Objects.isNull(originalPort)
                ? port()
                : originalPort;
    }

    @Override
    public String toString() {
        return "UserBindingPattern{" +
                "scheme='" + scheme() + '\'' +
                ", host='" + host() + '\'' +
                ", port='" + port().orElse(null) + '\'' +
                ", path='" + path() + '\'' +
                '}';
    }
}
