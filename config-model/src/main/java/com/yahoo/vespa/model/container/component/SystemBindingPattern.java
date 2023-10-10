// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

/**
 * A {@link BindingPattern} which is implicitly constructed by the model, e.g for built-in handlers and filter chains.
 *
 * @author bjorncs
 */
public class SystemBindingPattern extends BindingPattern {

    private SystemBindingPattern(String scheme, String host, String port, String path) { super(scheme, host, port, path); }
    private SystemBindingPattern(String binding) { super(binding); }

    public static SystemBindingPattern fromHttpPath(String path) { return new SystemBindingPattern("http", "*", null, path);}
    public static SystemBindingPattern fromPattern(String binding) { return new SystemBindingPattern(binding);}
    public static SystemBindingPattern fromHttpPortAndPath(String port, String path) { return new SystemBindingPattern("http", "*", port, path); }
    public static SystemBindingPattern fromHttpPortAndPath(int port, String path) { return new SystemBindingPattern("http", "*", Integer.toString(port), path); }
    public SystemBindingPattern withPort(int port) { return new SystemBindingPattern(scheme(), host(), Integer.toString(port), path()); }

    @Override
    public String toString() {
        return "SystemBindingPattern{" +
                "scheme='" + scheme() + '\'' +
                ", host='" + host() + '\'' +
                ", port='" + port().orElse(null) + '\'' +
                ", path='" + path() + '\'' +
                '}';
    }
}
