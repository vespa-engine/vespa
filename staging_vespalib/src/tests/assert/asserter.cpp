// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/assert.h>
#include <cassert>
#include <cstdlib>

int main(int argc, char *argv[]) {
    assert(argc == 3);
    const char * assertKey = argv[1];
    size_t assertCount = strtoul(argv[2], nullptr, 0);
    for (size_t i(0); i < assertCount; i++) {
        ASSERT_ONCE_OR_LOG(true, assertKey, 100);
        ASSERT_ONCE_OR_LOG(false, assertKey, 100);
    }
    assert(assertCount == vespalib::assert::getNumAsserts(assertKey));
    return 0;
}
