// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * This class holds the specifications of an application running message bus services. It is used for ensuring that a
 * {@link RoutingSpec} holds valid routing specifications.
 *
 * @author Simon Thoresen Hult
 */
public class ApplicationSpec {

    private final HashMap<String, HashSet<String>> services = new HashMap<String, HashSet<String>>();

    /**
     * Constructs a new instance of this class.
     */
    public ApplicationSpec() {
        // empty
    }

    /**
     * Implements the copy constructor.
     *
     * @param obj The object to copy.
     */
    public ApplicationSpec(ApplicationSpec obj) {
        add(obj);
    }

    /**
     * Adds the content of the given application to this.
     *
     * @param app The application whose content to copy.
     * @return This, to allow chaining.
     */
    public ApplicationSpec add(ApplicationSpec app) {
        for (Map.Entry<String, HashSet<String>> entry : app.services.entrySet()) {
            String protocol = entry.getKey();
            for (String service : entry.getValue()) {
                addService(protocol, service);
            }
        }
        return this;
    }

    /**
     * Adds a service name to the list of known services.
     *
     * @param protocol The protocol for which to add the service.
     * @param name     The service to add.
     * @return This, to allow chaining.
     */
    public ApplicationSpec addService(String protocol, String name) {
        if (!services.containsKey(protocol)) {
            services.put(protocol, new HashSet<String>());
        }
        services.get(protocol).add(name);
        return this;
    }

    /**
     * Determines whether or not the given service pattern matches any of the known services.
     *
     * @param protocol The protocol whose services to check.
     * @param pattern  The pattern to match.
     * @return True if at least one service was found.
     */
    public boolean isService(String protocol, String pattern) {
        if (services.containsKey(protocol)) {
            Pattern regex = toRegex(pattern);
            for (String service : services.get(protocol)) {
                if (regex.matcher(service).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Converts the given string pattern to a usable regex pattern object.
     *
     * @param pattern The string pattern to convert.
     * @return The corresponding regex pattern object.
     */
    private static Pattern toRegex(String pattern) {
        StringBuilder ret = new StringBuilder();
        ret.append("^");
        for (int i = 0; i < pattern.length(); i++) {
            ret.append(toRegex(pattern.charAt(i)));
        }
        ret.append("$");
        return Pattern.compile(ret.toString());
    }

    /**
     * Converts a single string pattern char to a regex string. This method is invoked by {@link #toRegex(String)} once
     * for each character in the string pattern.
     *
     * @param c The character to convert.
     * @return The corresponding regex pattern string.
     */
    private static String toRegex(char c) {
        switch (c) {
        case '*':
            return ".*";
        case '?':
            return ".";
        case '^':
        case '$':
        case '|':
        case '{':
        case '}':
        case '(':
        case ')':
        case '[':
        case ']':
        case '\\':
        case '+':
        case '.':
            return "\\" + c;
        default:
            return "" + c;
        }
    }
}
