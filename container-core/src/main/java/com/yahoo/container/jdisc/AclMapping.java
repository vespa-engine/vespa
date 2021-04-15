// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.jdisc;

/**
 * Mapping from request to action
 *
 * @author mortent
 */
public interface AclMapping {
    enum Action {create, read, update, delete};

    Action get(RequestView requestView);
}
