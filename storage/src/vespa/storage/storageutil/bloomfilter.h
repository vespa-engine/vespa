// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdio.h>
#include <inttypes.h>

class BloomFilter
{
private:
    BloomFilter(const BloomFilter &);
    BloomFilter& operator=(const BloomFilter &);

public:
        BloomFilter(int size, int hashes, uint32_t *buf = NULL);
        ~BloomFilter();

        bool check(const uint32_t *data, int len, bool add);
        bool check(const char *data, int len, bool add);
        bool check(const char *data, bool add);


    private:
        int _size;
        int _hashes;
        uint32_t *_buf;
        bool _mine;

        static const uint32_t MULT1 = 1500450271;
        static const uint32_t MULT2 = 2860486313U;
        uint32_t hash(const uint32_t *data, int len, uint32_t multiplier, uint32_t max);
        uint32_t hash(const char *data, int len, uint32_t multiplier, uint32_t max);
        uint32_t hash(const char *data, uint32_t multiplier, uint32_t max);

        bool check(uint32_t hash1, uint32_t hash2, bool add);
        bool isSet(uint32_t pos, bool set);

};

uint32_t
BloomFilter::hash(const uint32_t *data, int len, uint32_t multiplier, uint32_t max)
{
    uint32_t val = 1;
    for (int i = 0; i < len; i++) {
        val = (multiplier * val + data[i]) % max;
    }
    return val;
}

uint32_t
BloomFilter::hash(const char *data, int len, uint32_t multiplier, uint32_t max)
{
    uint32_t val = 1;
    for (int i = 0; i < len; i++) {
        val = (multiplier * val + data[i]) % max;
    }
    return val;
}

uint32_t
BloomFilter::hash(const char *data, uint32_t multiplier, uint32_t max)
{
    uint32_t val = 1;
    for (int i = 0; data[i]; i++) {
        val = (multiplier * val + data[i]) % max;
    }
    return val;
}


bool
BloomFilter::check(const uint32_t *data, int len, bool add)
{
    uint32_t hash1 = hash(data, len, MULT1, _size);
    uint32_t hash2 = hash(data, len, MULT2, _size);
    return check(hash1, hash2, add);
}

bool
BloomFilter::check(const char *data, int len, bool add)
{
    uint32_t hash1 = hash(data, len, MULT1, _size);
    uint32_t hash2 = hash(data, len, MULT2, _size);
    return check(hash1, hash2, add);
}
bool
BloomFilter::check(const char *data, bool add)
{
    uint32_t hash1 = hash(data, MULT1, _size);
    uint32_t hash2 = hash(data, MULT2, _size);
    return check(hash1, hash2, add);
}

bool
BloomFilter::check(uint32_t hash1, uint32_t hash2, bool add)
{
    bool found = true;
    for (int i = 0; i < _hashes; i++) {
        hash1 = (hash1 + hash2) % _size;
        hash2 = (hash2 + i) % _size;
        if (!isSet(hash1, add)) {
            if (!add) {
                return false;
            }
            found = false;
        }
    }
    return found;
}

bool
BloomFilter::isSet(uint32_t pos, bool add)
{
    if ((_buf[pos >> 5] & (1 << (pos & 31))) == 0) {
        if (add) {
            _buf[pos >> 5] |= (1 << (pos & 31));
        }
        return false;
    }
    return true;
}
