// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/alloc.h>
#include <vector>
#include <cassert>
#include <cstring>
#include <cstdlib>
#include <sys/resource.h>

using namespace vespalib::alloc;

int main(int argc, char *argv[]) {
    if (argc != 4) {
        return 77;
    }
    size_t virt = strtoul(argv[1], nullptr, 0);
    size_t blockSize = strtoul(argv[2], nullptr, 0);
    size_t numBlocks = strtoul(argv[3], nullptr, 0);
    rlimit virtualLimit;
    virtualLimit.rlim_cur = virt;
    virtualLimit.rlim_max = virt;
    assert(setrlimit(RLIMIT_AS, &virtualLimit) == 0);
    std::vector<Alloc> mappings;
    for (size_t i(0); i < numBlocks; i++) {
        mappings.emplace_back(Alloc::allocMMap(blockSize));
        memset(mappings.back().get(), 0xa5, mappings.back().size());
    }
    return 0;
}
