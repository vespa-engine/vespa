// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bloomfilter.h"
#include <cstdlib>
#include <cstring>

BloomFilter::BloomFilter(int size, int hashes, uint32_t *buf)
    : _size(size),
      _hashes(hashes),
      _buf(buf),
      _mine(false)
{
    if (!_buf) {
        _buf = new uint32_t[(_size / 32) + 1];
        memset(_buf, 0, ((_size / 32) + 1) * sizeof(uint32_t));
        _mine = true;
    }
}

BloomFilter::~BloomFilter()
{
    if (_mine) {
        delete [] _buf;
    }
}

/*
int main(int argc, char **argv)
{
    int size = atoi(argv[1]);
    int hashes = atoi(argv[2]);
    char buf[1000];
    BloomFilter bloom(size, hashes);

    while (fgets(buf, sizeof(buf), stdin)) {
        if (bloom.check(buf, true)) {
            printf("matched %s\n", buf);
        }
    }
}
*/
