// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/sanitizers.h>
#include <vespa/vespalib/util/size_literals.h>
#include <sys/resource.h>

using namespace search;
using namespace search::query;
using namespace search::streaming;

namespace {

[[maybe_unused]] void setMaxStackSize(rlim_t maxStackSize)
{
    struct rlimit limit;
    getrlimit(RLIMIT_STACK, &limit);
    limit.rlim_cur = maxStackSize;
    setrlimit(RLIMIT_STACK, &limit);
}

}


// NOTE: This test explicitly sets thread stack size and will fail due to
// a stack overflow if the stack usage increases.
TEST(StreamingQueryTest, testveryLongQueryResultingInBug6850778) {
    uint32_t NUMITEMS=20000;
#if defined(VESPA_USE_THREAD_SANITIZER) || defined(VESPA_USE_ADDRESS_SANITIZER)
    NUMITEMS = 10000;
#else
    setMaxStackSize(4_Mi);
#endif
    QueryBuilder<SimpleQueryNodeTypes> builder;
    for (uint32_t i=0; i <= NUMITEMS; i++) {
        builder.addAnd(2);
        builder.addStringTerm("a", "", 0, Weight(0));
        if (i < NUMITEMS) {
        } else {
            builder.addStringTerm("b", "", 0, Weight(0));
        }
    }
    Node::UP node = builder.build();
    auto queryTree = StackDumpCreator::createQueryTree(*node);
    QueryNodeResultFactory factory;
    Query q(factory, *queryTree);
    QueryTermList terms;
    QueryNodeRefList phrases;
    q.getLeaves(terms);
    ASSERT_EQ(NUMITEMS + 2, terms.size());
}

GTEST_MAIN_RUN_ALL_TESTS()
