// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/valgrind.h>
#include <cstring>

using namespace vespalib;

void testUninitializedUser()
{
    char buf[7];
    buf[0] = 7;
    buf[5] = 7;
    Valgrind::testUninitialized(buf, sizeof(buf));
}

void testUninitializedSystemCall()
{
    char buf[7];
    buf[0] = 7;
    buf[5] = 7;
    Valgrind::testSystemCall(buf, sizeof(buf));
}
void testInitializedUser()
{
    char buf[7];
    memset(buf, 0, sizeof(buf));
    Valgrind::testUninitialized(buf, sizeof(buf));
}

void testInitializedSystemCall()
{
    char buf[7];
    memset(buf, 0, sizeof(buf));
    Valgrind::testSystemCall(buf, sizeof(buf));
}

int main(int, char **argv) {
    if (strcmp(argv[1], "testInitializedUser") == 0) {
        testInitializedUser();
    } else if (strcmp(argv[1], "testInitializedSystemCall") == 0) {
        testInitializedSystemCall();
    } else if (strcmp(argv[1], "testUninitializedUser") == 0) {
        testUninitializedUser();
    } else if (strcmp(argv[1], "testUninitializedSystemCall") == 0) {
        testUninitializedSystemCall();
    } else {
        testInitializedUser();
    }
    return 0;
}
