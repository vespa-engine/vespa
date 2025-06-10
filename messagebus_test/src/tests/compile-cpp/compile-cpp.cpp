// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("compile-cpp_test");
#include <vespa/messagebus/routing/route.h>

void test_cpp_compile() {
    mbus::Route r;
}

int main(int, char **) {
    test_cpp_compile();
    return 0;
}
