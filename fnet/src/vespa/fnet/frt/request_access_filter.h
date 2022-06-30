// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

class FRT_RPCRequest;

/**
 * An RPC request access filter will, if provided during method registration, be
 * invoked _prior_ to any RPC handler callback invocation for that method. It allows
 * for implementing method-specific authorization handling, logging etc.
 *
 * Must be thread safe.
 */
class FRT_RequestAccessFilter {
public:
    virtual ~FRT_RequestAccessFilter() = default;

    /**
     * Iff true is returned, the request is allowed through and the RPC callback
     * will be invoked as usual. If false, the request is immediately failed back
     * to the caller with an error code.
     */
    [[nodiscard]] virtual bool allow(FRT_RPCRequest&) const noexcept = 0;
};
