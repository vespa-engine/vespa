// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executeinfo.h"

namespace search::queryeval {

const ExecuteInfo ExecuteInfo::TRUE(true, 1.0);
const ExecuteInfo ExecuteInfo::FALSE(false, 1.0);

ExecuteInfo
ExecuteInfo::create(bool strict) {
    return create(strict, 1.0);
}

ExecuteInfo
ExecuteInfo::create(bool strict, double hitRate) {
    return ExecuteInfo(strict, hitRate);
}

}
