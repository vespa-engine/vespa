// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/base/documentid.h>
#include <cinttypes>

using namespace document;

int main(int argc, char *argv[])
{
    if (argc < 3) {
        fprintf(stderr, "Usage %s <docid> <count>\n", argv[0]);
    }
    vespalib::string s(argv[1]);
    uint64_t n = strtoul(argv[2], nullptr, 0);
    printf("Creating documentid '%s' %" PRIu64 " times\n", s.c_str(), n);
    printf("sizeof(IdString)=%ld,  sizeof(IdIdString)=%ld\n", sizeof(IdString), sizeof(IdString));
    for (uint64_t i=0; i < n; i++) {
        IdString id(s);
        (void) id;
    }
    return 0;
}

