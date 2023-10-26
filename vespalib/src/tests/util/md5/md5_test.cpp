// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/md5.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace vespalib {

string md5_hash_of(stringref buffer) {
    unsigned char hash_out[16]; // Always 128 bits.
    fastc_md5sum(buffer.data(), buffer.size(), hash_out);
    asciistream os;
    for (int i = 0; i < 16; ++i) {
        os << hex << setw(2) << setfill('0') << static_cast<unsigned int>(hash_out[i]);
    }
    return os.str();
}

// https://www.nist.gov/itl/ssd/software-quality-group/nsrl-test-data
// We only include the informal test vectors here.
TEST("MD5 output matches NIST test vectors") {
    EXPECT_EQUAL("900150983cd24fb0d6963f7d28e17f72", md5_hash_of("abc"));
    EXPECT_EQUAL("8215ef0796a20bcaaae116d3876c664a",
                 md5_hash_of("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"));
    EXPECT_EQUAL("7707d6ae4e027c70eea2a935c2296f21", md5_hash_of(string(1'000'000, 'a')));
}

// https://en.wikipedia.org/wiki/MD5#MD5_hashes
TEST("MD5 output matches Wikipedia test vectors") {
    EXPECT_EQUAL("d41d8cd98f00b204e9800998ecf8427e", md5_hash_of(""));
    EXPECT_EQUAL("9e107d9d372bb6826bd81d3542a419d6", md5_hash_of("The quick brown fox jumps over the lazy dog"));
    EXPECT_EQUAL("e4d909c290d0fb1ca068ffaddf22cbd0", md5_hash_of("The quick brown fox jumps over the lazy dog."));
}

}

TEST_MAIN() { TEST_RUN_ALL(); }

