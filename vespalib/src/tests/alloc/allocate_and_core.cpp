// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/alloc.h>
#include <string.h>
#include <cassert>

using namespace vespalib::alloc;

int main(int argc, char *argv[]) {
    (void) argc;
    (void) argv;
    Alloc small(Alloc::allocMMap(0x400000)); //4M
    memset(small.get(), 0x55, small.size());
    Alloc large(Alloc::allocMMap(0x4000000)); //640M
    memset(large.get(), 0x66, large.size());
    assert(false);
}
