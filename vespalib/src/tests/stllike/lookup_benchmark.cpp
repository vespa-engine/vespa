// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stddef.h>
#include <stdlib.h>
#include <stdio.h>
#include <map>
#include <set>
#include <tr1/unordered_set>
#include <vector>
#include <algorithm>
#include <vespa/vespalib/stllike/hash_set.hpp>

template <typename S>
void fill(S & s, size_t count)
{
    for(size_t i(0); i < count; i++) {
        s.insert(i);
    }
}

template <typename S>
size_t lookup_bench(S & s, size_t count, size_t rep)
{
    size_t sum(0);
    typename S::const_iterator e(s.end());
    for (size_t j(0); j < rep; j++) {
        for (size_t i(0); i < count; i++) {
            if (e != s.find(i)) {
                sum++;
            }
        }
    }
    return sum;
}

template <typename S>
size_t bench(S & set, size_t sz, size_t numLookups)
{
    fill(set, sz);
    return lookup_bench(set, sz, numLookups/sz);
}

size_t benchMap(size_t sz, size_t numLookups)
{
    std::set<uint32_t> set;
    return bench(set, sz, numLookups);
}

size_t benchHashStl(size_t sz, size_t numLookups)
{
    std::tr1::unordered_set<uint32_t> set;
    return bench(set, sz, numLookups);
}

size_t benchHashVespaLib(size_t sz, size_t numLookups)
{
    vespalib::hash_set<uint32_t> set;
    return bench(set, sz, numLookups);
}

size_t benchHashVespaLib2(size_t sz, size_t numLookups)
{
    vespalib::hash_set<uint32_t, vespalib::hash<uint32_t>, std::equal_to<uint32_t>, vespalib::hashtable_base::and_modulator > set;
    return bench(set, sz, numLookups);
}

int main(int argc, char *argv[])
{
    size_t count(1000);
    size_t rep(10000000);
    char type('m');
    if (argc >= 2) {
        type = argv[1][0];
    }
    if (argc >= 3) {
        count = strtoul(argv[2], NULL, 0);
    }
    if (argc >= 4) {
        rep = strtoul(argv[3], NULL, 0);
    }
    std::vector<const char *> description(256);
    description['m'] = "std::set";
    description['h'] = "std::hash_set";
    description['g'] = "vespalib::hash_set";
    description['G'] = "vespalib::hash_set with simple and modulator.";
    size_t found(0);
    switch (type) {
    case 'm': found = benchMap(count, rep); break;
    case 'h': found = benchHashStl(count, rep); break;
    case 'g': found = benchHashVespaLib(count, rep); break;
    case 'G': found = benchHashVespaLib2(count, rep); break;
    default:
        printf("'m' = %s\n", description[type]);
        printf("'h' = %s\n", description[type]);
        printf("'g' = %s\n", description[type]);
        printf("'G' = %s\n", description[type]);
        printf("Unspecified type %c. Running map lookup benchmark\n", type);
        exit(1);
        break;
    }
    printf("Running test '%c' = %s, result = %ld found values\n", type, description[type], found);
}

