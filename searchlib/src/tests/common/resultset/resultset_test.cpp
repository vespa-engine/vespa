// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for resultset.

#include <vespa/log/log.h>
LOG_SETUP("resultset_test");

#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/arraysize.h>

using namespace search;
using vespalib::arraysize;

namespace {

void addHit(ResultSet &set, unsigned int doc_id, double rank) {
    set.push_back(RankedHit(doc_id, rank));
}

TEST("require that mergeWithOverflow works") {
    ResultSet set1;
    set1.allocArray(10);
    addHit(set1, 2, 4.2);
    addHit(set1, 4, 3.2);
    BitVector::UP bit_vector = BitVector::create(20);
    bit_vector->setBit(2);
    bit_vector->setBit(4);
    bit_vector->setBit(7);
    bit_vector->invalidateCachedCount();
    set1.setBitOverflow(std::move(bit_vector));
    EXPECT_EQUAL(3u, set1.getNumHits());
    set1.mergeWithBitOverflow();
    EXPECT_EQUAL(3u, set1.getNumHits());
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
