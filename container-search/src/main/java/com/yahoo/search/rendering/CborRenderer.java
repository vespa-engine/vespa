// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import java.util.concurrent.Executor;

/**
 * CBOR renderer for search results.
 * The result when rendered as CBOR should be semantically the same 
 * as JSON, i.e. if converted back to JSON the result should be identical.
 * This makes it trivial to start using the more efficient format.
 * The easiest way to do that is to ensure that it's done by the same code,
 * which is the reason this class is, and should remain, quite nearly empty.
 * @author andreer
 */
public class CborRenderer extends JsonRenderer {

    public CborRenderer() {
        this(null);
    }

    public CborRenderer(Executor executor) {
        super(executor);
    }

    @Override
    RenderTarget defaultRenderTarget() {
        return RenderTarget.Cbor;
    }

}
