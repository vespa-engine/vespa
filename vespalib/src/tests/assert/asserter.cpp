// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/assert.h>
#include <cassert>
#include <cstdlib>
#include <fstream>
#include <string>

int main(int argc, char *argv[]) {
    assert(argc == 3);
    const char * assertKey = argv[1];
    size_t assertCount = strtoul(argv[2], nullptr, 0);
    for (size_t i(0); i < assertCount; i++) {
        ASSERT_ONCE_OR_LOG(true, assertKey, 100);
        ASSERT_ONCE_OR_LOG(false, assertKey, 100);
    }
    std::string filename = vespalib::assert::getAssertLogFileName(assertKey);
    std::ifstream is(filename.c_str());
    assert(is);
    std::string line;
    std::getline(is, line);
    printf("%s\n", filename.c_str());
    assert(line.find(assertKey) != std::string::npos);
    assert(assertCount == vespalib::assert::getNumAsserts(assertKey));
    return 0;
}
