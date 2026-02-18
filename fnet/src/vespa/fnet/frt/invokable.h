// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

class FRT_RPCRequest;

class FRT_Invokable {
public:
    virtual ~FRT_Invokable() = default;
};

typedef void (FRT_Invokable::*FRT_METHOD_PT)(FRT_RPCRequest*);

namespace fnet::internal {

template <class T> using frt_method_precast_pt = void (T::*)(FRT_RPCRequest*);

template <class T> FRT_METHOD_PT frt_method_pt_cast(frt_method_precast_pt<T> pt) { return (FRT_METHOD_PT)pt; }

} // namespace fnet::internal

#define FRT_METHOD(pt) (fnet::internal::frt_method_pt_cast(&pt))
