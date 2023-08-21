// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

/**
 * A {@link BindingPattern} which is constructed directly from a user provided 'binding' element from services.xml.
 *
 * @author bjorncs
 */
public class UserBindingPattern extends BindingPattern {

    private UserBindingPattern(String scheme, String host, String port, String path) { super(scheme, host, port, path); }
    private UserBindingPattern(String binding) { super(binding); }

    public static UserBindingPattern fromHttpPath(String path) { return new UserBindingPattern("http", "*", null, path); }
    public static UserBindingPattern fromPattern(String binding) { return new UserBindingPattern(binding); }
    public UserBindingPattern withPort(int port) { return new UserBindingPattern(scheme(), host(), Integer.toString(port), path()); }

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
