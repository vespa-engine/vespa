// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for hyperloglog.

#include <vespa/log/log.h>
LOG_SETUP("hyperloglog_test");

#include <vespa/searchlib/grouping/hyperloglog.h>
#include <vespa/vespalib/objects/nboserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/testkit/testapp.h>

using vespalib::NBOSerializer;
using vespalib::nbostream;
using namespace search;

namespace {

TEST("require that hyperloglog changes from sparse to normal sketch") {
    HyperLogLog<> hll;
    for (size_t i = 0; i < 256; ++i) {
        EXPECT_TRUE(dynamic_cast<const SparseSketch<> *>(&hll.getSketch()));
        EXPECT_EQUAL(1, hll.aggregate(i));
    }
    EXPECT_TRUE(dynamic_cast<const SparseSketch<> *>(&hll.getSketch()));
    EXPECT_EQUAL(23, hll.aggregate(256));
    EXPECT_TRUE(dynamic_cast<const NormalSketch<> *>(&hll.getSketch()));
}

TEST("require that hyperloglog can be (de)serialized") {
    HyperLogLog<> hll;
    for (size_t i = 0; i < 256; ++i) {
        EXPECT_EQUAL(1, hll.aggregate(i));
    }
    nbostream stream;
    NBOSerializer serializer(stream);

    // Serializes with sparse sketch
    hll.serialize(serializer);
    HyperLogLog<> hll2;
    hll2.deserialize(serializer);
    EXPECT_TRUE(dynamic_cast<const SparseSketch<> *>(&hll2.getSketch()));
    EXPECT_EQUAL(hll.getSketch(), hll2.getSketch());

    // Serializes with normal sketch.
    EXPECT_EQUAL(23, hll2.aggregate(256));
    hll2.serialize(serializer);
    hll.deserialize(serializer);
    EXPECT_TRUE(dynamic_cast<const NormalSketch<> *>(&hll.getSketch()));
    EXPECT_EQUAL(hll2.getSketch(), hll.getSketch());
}

TEST("require that sparse hyperloglogs can be merged") {
    HyperLogLog<> hll;
    for (size_t i = 0; i < 100; ++i) {
        EXPECT_EQUAL(1, hll.aggregate(i));
    }
    HyperLogLog<> hll2;
    for (size_t i = 100; i < 255; ++i) {
        EXPECT_EQUAL(1, hll2.aggregate(i));
    }
    hll.merge(hll2);
    EXPECT_TRUE(dynamic_cast<const SparseSketch<> *>(&hll.getSketch()));

    EXPECT_EQUAL(1, hll2.aggregate(255));
    hll.merge(hll2);
    EXPECT_TRUE(dynamic_cast<const NormalSketch<> *>(&hll.getSketch()));
}

TEST("require that mixed hyperloglogs can be merged") {
    HyperLogLog<> hll;
    for (size_t i = 0; i < 256; ++i) {
        EXPECT_EQUAL(1, hll.aggregate(i));
    }
    EXPECT_EQUAL(23, hll.aggregate(256));  // normal
    HyperLogLog<> hll2;
    for (size_t i = 100; i < 255; ++i) {
        EXPECT_EQUAL(1, hll2.aggregate(i));  // sparse
    }
    hll.merge(hll2);  // normal + sparse
    hll2.merge(hll);  // sparse + normal
    EXPECT_EQUAL(hll.getSketch(), hll2.getSketch());

    EXPECT_EQUAL(23, hll2.aggregate(500));
    hll.merge(hll2);  // normal + normal
    EXPECT_EQUAL(hll.getSketch(), hll2.getSketch());
    EXPECT_EQUAL(0, hll.aggregate(500));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
