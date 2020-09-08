// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rpc_target.h"
#include <vespa/fnet/frt/target.h>

namespace storage::rpc {

RpcTarget::RpcTarget(FRT_Target* target, vespalib::stringref spec, uint32_t sb_generation)
    : _target(target),
      _spec(spec),
      _sb_generation(sb_generation)
{}

RpcTarget::~RpcTarget() {
    _target->SubRef();
}

}
