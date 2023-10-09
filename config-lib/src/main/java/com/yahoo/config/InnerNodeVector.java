// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.util.List;

/**
 * @author gjoranv
 */
public class InnerNodeVector<NODE extends InnerNode> extends NodeVector<NODE> {

    public InnerNodeVector(List<NODE> nodes) {
        vector.addAll(nodes);
    }

}
