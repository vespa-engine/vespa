// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request.properties;

/**
 * This interface publicly exposes the clone method.
 * Classes which are used in request properties may implement this to allow faster cloning of the request.
 *
 * @author bratseth
 * @since  5.66
 * @deprecated Use com.yahoo.lang.PublicCloneable instead
 */
@Deprecated(forRemoval = true) // TODO: Remove on Vespa 9
public interface PublicCloneable<T> extends Cloneable {

    T clone();

}
