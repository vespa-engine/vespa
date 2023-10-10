// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.parser;

import java.util.List;

/**
 * @author Tony Vaagenes
 */
public interface DispatchFormHandler {
    Object dispatch(String name, List<Object> arguments, Object dispatchContext);
}
