// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

/**
 * Request access filter is invoked before any call to {@link Method#invoke(Request)}.
 * If {@link #allow(Request)} returns false, the method is not invoked, and the request is failed with error
 * {@link ErrorCode#PERMISSION_DENIED}.
 *
 * @author bjorncs
 */
public interface RequestAccessFilter {

    RequestAccessFilter ALLOW_ALL = __ -> true;

    boolean allow(Request r);

}
