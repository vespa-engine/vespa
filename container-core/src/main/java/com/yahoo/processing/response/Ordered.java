// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.response;

/**
 * This is an <i>optional marker interface</i>.
 * DataLists may implement this to return false to indicate that the order of the elements of
 * the list is insignificant. The usage of this is to allow the content of a list to be rendered in the order
 * in which it completes rather than in the order in which it is added to the list.
 *
 * @author  bratseth
 */
public interface Ordered {

    /** Returns false if the data in this list can be returned in any order. Default: true, meaning the order matters */
    boolean isOrdered();

}
