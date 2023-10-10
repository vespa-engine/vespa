// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/queryeval/sorted_hit_sequence.h>

using search::queryeval::SortedHitSequence;
using Hits = std::vector<SortedHitSequence::Hit>;
using Refs = std::vector<SortedHitSequence::Ref>;

Hits hits({{1,10.0},{2,30.0},{3,20.0}});
Refs refs({1,2,0});

TEST("require that empty hit sequence is empty") {
    EXPECT_TRUE(!SortedHitSequence(nullptr, nullptr, 0).valid());
    EXPECT_TRUE(!SortedHitSequence(&hits[0], &refs[0], 0).valid());
}

TEST("require that sorted hit sequence can be iterated") {
    SortedHitSequence seq(&hits[0], &refs[0], refs.size());
    for (const auto &expect: Hits({{2,30.0},{3,20.0},{1,10.0}})) {
        ASSERT_TRUE(seq.valid());
        EXPECT_EQUAL(expect.first, seq.get().first);
        EXPECT_EQUAL(expect.second, seq.get().second);
        seq.next();
    }
    EXPECT_TRUE(!seq.valid());
}

TEST_MAIN() { TEST_RUN_ALL(); }
