// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "destructor_callbacks.h"
#include "gate.h"

namespace vespalib {

GateCallback::~GateCallback() {
    _gate.countDown();
}

}
