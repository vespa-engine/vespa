// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rpcrequest.h"
#include <vespa/vespalib/util/ref_counted.h>
#include <variant>

class FRT_Invokable
{
public:
    virtual ~FRT_Invokable() = default;
};

using FRT_METHOD0_PT = void (FRT_Invokable::*)(FRT_RPCRequest *);

using FRT_REFCOUNTED_REQUEST = vespalib::ref_counted<FRT_RPCRequest>;

using FRT_METHOD1_PT = void (FRT_Invokable::*)(FRT_REFCOUNTED_REQUEST);

using FRT_METHOD_PT = std::variant<FRT_METHOD0_PT, FRT_METHOD1_PT>;

namespace fnet::internal {

template <class T>
using frt_method0_precast_pt = void (T::*)(FRT_RPCRequest *);

template <class T>
using frt_method1_precast_pt = void (T::*)(FRT_REFCOUNTED_REQUEST);

template <class T>
FRT_METHOD_PT
frt_method_pt_cast(frt_method0_precast_pt<T> pt)
{
  return (FRT_METHOD0_PT) pt;
}

template <class T>
FRT_METHOD_PT
frt_method_pt_cast(frt_method1_precast_pt<T> pt)
{
    return (FRT_METHOD1_PT) pt;
}

}

#define FRT_METHOD(pt) (fnet::internal::frt_method_pt_cast(&pt))
