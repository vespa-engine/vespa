// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import java.util.Collection;

/**
 * Legacy filter config. Prefer to use a regular stringly typed config class for new filters.
 *
 * @author tejalk
 */
public interface FilterConfig {

    /** Returns the filter-name of this filter */
    String getFilterName();

    /** Returns the filter-class of this filter */
    String getFilterClass();

    /**
     * Returns a String containing the value of the
     * named initialization parameter, or null if
     * the parameter does not exist.
     *
     * @param name	a String specifying the name of the initialization parameter
     * @return a String containing the value of the initialization parameter
     */
    String getInitParameter(String name);

    /**
     * Returns the boolean value of the init parameter. If not present returns default value
     *
     * @return boolean value of init parameter
     */
    boolean getBooleanInitParameter(String name, boolean defaultValue);

    /**
     * Returns the names of the filter's initialization parameters as an Collection of String objects,
     * or an empty Collection if the filter has no initialization parameters.
     */
    Collection<String> getInitParameterNames();

}
