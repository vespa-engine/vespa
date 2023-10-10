// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

class FRT_Target;

namespace storage::rpc {

/**
 * Simple wrapper API to access a FRT_Target.
 */
class RpcTarget {
public:
    virtual ~RpcTarget() = default;
    virtual FRT_Target* get() noexcept = 0;
    virtual bool is_valid() const noexcept = 0;
    virtual const vespalib::string& spec() const noexcept = 0;
};

}
