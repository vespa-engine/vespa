// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gatecallback.h"
#include <vespa/vespalib/util/sync.h>

namespace search {

GateCallback::~GateCallback() {
    _gate.countDown();
}

}
