// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executeinfo.h"

namespace search::queryeval {

const ExecuteInfo ExecuteInfo::TRUE(true, 1.0, nullptr, vespalib::ThreadBundle::trivial(), true, true);
const ExecuteInfo ExecuteInfo::FALSE(false, 1.0, nullptr, vespalib::ThreadBundle::trivial(), true, true);

}
