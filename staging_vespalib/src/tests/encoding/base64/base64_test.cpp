// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/encoding/base64.h>
#include <vector>

using namespace vespalib;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("base64_test");

        // Basic test without padding
    std::string source = "No need to pad this string.";
    std::string encoded = Base64::encode(source);
    std::string expected = "Tm8gbmVlZCB0byBwYWQgdGhpcyBzdHJpbmcu";
    std::string decoded = Base64::decode(encoded);

    EXPECT_EQUAL(expected, encoded);
    EXPECT_EQUAL(source, decoded);

    EXPECT_TRUE(static_cast<uint32_t>(
            Base64::getMaximumEncodeLength(source.size())) >= encoded.size());
    EXPECT_TRUE(static_cast<uint32_t>(
            Base64::getMaximumDecodeLength(encoded.size())) >= source.size());

        // Basic string that needs padding
    source = "This string will need to be padded.";
    encoded = Base64::encode(source);
    expected = "VGhpcyBzdHJpbmcgd2lsbCBuZWVkIHRvIGJlIHBhZGRlZC4=";
    decoded = Base64::decode(encoded);

    EXPECT_EQUAL(expected, encoded);
    EXPECT_EQUAL(source, decoded);

    EXPECT_TRUE(static_cast<uint32_t>(
            Base64::getMaximumEncodeLength(source.size())) >= encoded.size());
    EXPECT_TRUE(static_cast<uint32_t>(
            Base64::getMaximumDecodeLength(encoded.size())) >= source.size());

        // Check that max sizes are good for whatever input sizes
    source = "";
    for (uint32_t i=0; i<100; ++i) {
        source += "a";
            // Code will assert if -1 is returned from either
            // getMaximumEncodeLength() or getMaximumDecodeLength().
        encoded = Base64::encode(source);
        decoded = Base64::decode(encoded);
        EXPECT_EQUAL(source, decoded);
    }

        // Check that -1 is returned on too little space when encoding
    source = "Checking that -1 is returned when not enough space to encode";
    std::vector<char> buffer(100, '\0');
    uint32_t minSizeNeeded = 81;
    for (uint32_t i=0; i<minSizeNeeded; ++i) {
        EXPECT_EQUAL(-1, Base64::encode(source.c_str(), source.size(),
                                      &buffer[0], i));
    }
    EXPECT_EQUAL(80, Base64::encode(source.c_str(), source.size(),
                                  &buffer[0], minSizeNeeded));
    EXPECT_EQUAL(Base64::encode(source), std::string(&buffer[0], 80));
    EXPECT_TRUE(minSizeNeeded <= static_cast<uint32_t>(
                Base64::getMaximumEncodeLength(source.size())));

    EXPECT_TRUE(buffer[80] == '\0');

        // Check that -1 is returned on too little space when decoding
    encoded = Base64::encode(source);
    minSizeNeeded = 60;
    for (uint32_t i=0; i<minSizeNeeded; ++i) {
        EXPECT_EQUAL(-1, Base64::decode(encoded.c_str(), encoded.size(),
                                      &buffer[0], i));
    }
    EXPECT_EQUAL(60, Base64::decode(encoded.c_str(), encoded.size(),
                                  &buffer[0], minSizeNeeded));
    EXPECT_EQUAL(source, std::string(&buffer[0], 60));

    TEST_DONE();
}
