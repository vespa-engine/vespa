// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/size_literals.h>
#include <sys/resource.h>

using namespace search;
using namespace search::query;
using namespace search::streaming;

#ifndef __SANITIZE_ADDRESS__
#if defined(__has_feature)
#if __has_feature(address_sanitizer)
#define __SANITIZE_ADDRESS__
#endif
#endif
#endif

namespace {

void setMaxStackSize(rlim_t maxStackSize)
{
    struct rlimit limit;
    getrlimit(RLIMIT_STACK, &limit);
    limit.rlim_cur = maxStackSize;
    setrlimit(RLIMIT_STACK, &limit);
}

}


// NOTE: This test explicitly sets thread stack size and will fail due to
// a stack overflow if the stack usage increases.
TEST("testveryLongQueryResultingInBug6850778") {
    const uint32_t NUMITEMS=20000;
#ifdef __SANITIZE_ADDRESS__
    setMaxStackSize(12_Mi);
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
    vespalib::string stackDump = StackDumpCreator::create(*node);

    QueryNodeResultFactory factory;
    Query q(factory, stackDump);
    QueryTermList terms;
    QueryNodeRefList phrases;
    q.getLeafs(terms);
    ASSERT_EQUAL(NUMITEMS + 2, terms.size());
}

TEST_MAIN() { TEST_RUN_ALL(); }
