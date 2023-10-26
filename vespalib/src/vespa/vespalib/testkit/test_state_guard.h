// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "test_master.h"

namespace vespalib {

#ifndef IAM_DOXYGEN
struct TestStateGuard {
    TestStateGuard(const char *file, uint32_t line, const char *msg) {
        TestMaster::master.pushState(file, line, msg);
    }
    ~TestStateGuard() { TestMaster::master.popState(); }
};
#endif

} // namespace vespalib

