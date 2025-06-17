// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rfc_sha1.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/sha1.h>
#include <string>
#include <cassert>

using namespace vespalib;

struct Digest {
    char buf[20];
    Digest() {
        for (size_t i = 0; i < 20; ++i) {
            buf[i] = ((rand() >> 12) & 0xff);
        }
    }
    std::string as_string() const {
        const char *sym = "0123456789ABCDEF";
        std::string res;
        for (size_t i = 0; i < 20; ++i) {
            res += sym[(buf[i] >> 4) & 0xf];
            res += sym[buf[i] & 0xf];
        }
        return ("0x" + res);
    }
    bool operator==(const Digest &rhs) const {
        for (size_t i = 0; i < 20; ++i) {
            if (buf[i] != rhs.buf[i]) {
                return false;
            }
        }
        return true;
    }
};

std::ostream &operator <<(std::ostream &out, const Digest &digest) {
    out << digest.as_string();
    return out;    
}

Digest digest(const char *data, size_t size) {
    Digest result;
    Sha1::hash(data, size, result.buf, 20);
    return result;
}

Digest rfc_digest(const char *data, size_t size) {
    Digest result;
    SHA1Context ctx;
    SHA1Reset(&ctx);
    SHA1Input(&ctx, (const uint8_t *)data, size);
    SHA1Result(&ctx, (unsigned char *)result.buf);
    return result;
}

struct Data {
    char buf[5000];
    Data() {
        srand(42);
        for (size_t i = 0; i < sizeof(buf); ++i) {
            buf[i] = ((rand() >> 12) & 0xff);
        }
    }
    size_t max() const { return sizeof(buf); }
    Digest inc_digest(std::initializer_list<size_t> chunks) const {
        Digest result;
        Sha1 sha;
        uint32_t ofs = 0;
        for (auto chunk: chunks) {
            assert(ofs + chunk <= max());
            sha.process(buf + ofs, chunk);
            ofs += chunk;
        }
        assert(ofs == max());
        sha.get_digest(result.buf);
        return result;
    }
};

TEST(Sha1Test, require_that_reference_implementation_passes_SHA1_smoke_test) {
    EXPECT_EQ("0xA9993E364706816ABA3E25717850C26C9CD0D89D", rfc_digest("abc", 3).as_string());
}

TEST(Sha1Test, require_that_production_implementation_passes_SHA1_smoke_test) {
    EXPECT_EQ("0xA9993E364706816ABA3E25717850C26C9CD0D89D", digest("abc", 3).as_string());
}

TEST(Sha1Test, require_that_random_data_hashes_to_the_same_as_reference_implementation) {
    Data f1;
    for (size_t size = 0; size <= f1.max(); ++size) {
        EXPECT_EQ(rfc_digest(f1.buf, size), digest(f1.buf, size));
    }
}

TEST(Sha1Test, require_that_incremental_and_all_in_one_hashing_produces_the_same_result) {
    Data f1;
    EXPECT_EQ(digest(f1.buf, f1.max()),
                 f1.inc_digest({ 1000, 1000, 1000, 1000, 1000 }));
    EXPECT_EQ(digest(f1.buf, f1.max()),
                 f1.inc_digest({ 10, 10, 10, 10, 10, 10, 4, 64, 64, 64, 128, 75, 75, 2500, 1966 }));
    EXPECT_EQ(digest(f1.buf, f1.max()),
                 f1.inc_digest({ 64, 64, 128, 256, 10, 10, 10, 10, 10, 10, 10, 100, 4318 }));
}

GTEST_MAIN_RUN_ALL_TESTS()
