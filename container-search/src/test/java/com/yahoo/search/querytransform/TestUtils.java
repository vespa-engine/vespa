// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.query.Item;

import com.yahoo.search.Result;

public class TestUtils {

    public static Item getQueryTreeRoot(Result result) {
        return result.getQuery().getModel().getQueryTree().getRoot();
    }

}
