// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <cstddef>
#include <cstdlib>
#include <cstdio>
#include <set>
#include <unordered_set>
#include <vector>
//#define XXH_INLINE_ALL
#include <xxhash.h>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <vespa/vespalib/stllike/hash_map.hpp>

template <typename S>
void fill(S & s, size_t count)
{
    for(size_t i(0); i < count; i++) {
        s.insert(i);
    }
}

template <typename M>
void fillM(M & m, size_t count)
{
    for(size_t i(0); i < count; i++) {
        m[i] = i;
    }
}

template <typename S>
size_t lookup_bench(S & s, size_t count, size_t rep)
{
    std::vector<uint32_t> keys(count);
    for (uint32_t & key : keys) {
        key = rand()%count;
    }
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

template <typename M>
size_t benchM(M & map, size_t sz, size_t numLookups)
{
    fillM(map, sz);
    return lookup_bench(map, sz, numLookups/sz);
}

size_t benchMap(size_t sz, size_t numLookups)
{
    std::set<uint32_t> set;
    return bench(set, sz, numLookups);
}

size_t benchHashStl(size_t sz, size_t numLookups)
{
    std::unordered_set<uint32_t> set;
    return bench(set, sz, numLookups);
}

size_t benchHashVespaLib(size_t sz, size_t numLookups)
{
    vespalib::hash_set<uint32_t> set(3*sz);
    return bench(set, sz, numLookups);
}

size_t benchHashVespaLib2(size_t sz, size_t numLookups)
{
    vespalib::hash_set<uint32_t, vespalib::hash<uint32_t>, std::equal_to<uint32_t>, vespalib::hashtable_base::and_modulator > set(3*sz);
    return bench(set, sz, numLookups);
}

size_t benchHashMapVespaLib(size_t sz, size_t numLookups)
{
    vespalib::hash_map<uint32_t, uint32_t> set(3*sz);
    return benchM(set, sz, numLookups);
}

size_t benchHashMapVespaLib2(size_t sz, size_t numLookups)
{
    vespalib::hash_map<uint32_t, uint32_t, vespalib::hash<uint32_t>, std::equal_to<uint32_t>, vespalib::hashtable_base::and_modulator > set(3*sz);
    return benchM(set, sz, numLookups);
}

std::unique_ptr<char []> createData(size_t sz) {
    auto data = std::make_unique<char []>(sz);
    for (size_t i(0); i < sz; i++) {
        data.get()[i] = i + '0';
    }
    return data;
}

size_t benchXXHash32(size_t sz, size_t numLookups) {
    auto data = createData(sz);
    size_t sum(0);
    for (size_t i(0); i < numLookups; i++) {
        sum += XXH32(data.get(), sz, 0);
    }
    return sum;
}

size_t benchXXHash64(size_t sz, size_t numLookups) {
    auto data = createData(sz);
    size_t sum(0);
    for (size_t i(0); i < numLookups; i++) {
        sum += XXH64(data.get(), sz, 0);
    }
    return sum;
}

size_t benchLegacyHash(size_t sz, size_t numLookups) {
    auto data = createData(sz);
    size_t sum(0);
    for (size_t i(0); i < numLookups; i++) {
        sum += vespalib::hashValue(data.get(), sz);
    }
    return sum;
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
        count = strtoul(argv[2], nullptr, 0);
    }
    if (argc >= 4) {
        rep = strtoul(argv[3], nullptr, 0);
    }
    std::vector<const char *> description(256);
    description['m'] = "std::set";
    description['h'] = "std::hash_set";
    description['g'] = "vespalib::hash_set";
    description['G'] = "vespalib::hash_set with simple and modulator.";
    description['k'] = "vespalib::hash_map";
    description['K'] = "vespalib::hash_map with simple and modulator.";
    description['x'] = "xxhash32";
    description['X'] = "xxhash64";
    description['l'] = "legacy";
    size_t found(0);
    switch (type) {
        case 'm': found = benchMap(count, rep); break;
        case 'h': found = benchHashStl(count, rep); break;
        case 'g': found = benchHashVespaLib(count, rep); break;
        case 'G': found = benchHashVespaLib2(count, rep); break;
        case 'k': found = benchHashMapVespaLib(count, rep); break;
        case 'K': found = benchHashMapVespaLib2(count, rep); break;
        case 'x': found = benchXXHash32(count, rep); break;
        case 'X': found = benchXXHash64(count, rep); break;
        case 'l': found = benchLegacyHash(count, rep); break;
        default:
            for (char c : "mhgGkKxXl") {
                printf("'%c' = %s\n", c, description[c]);
            }
            return 1;
    }
    printf("Running test '%c' = %s, result = %ld found values\n", type, description[type], found);
    return 0;
}

