// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/search_context.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/test/attribute_builder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/simple_thread_bundle.h>

namespace search::attribute {
namespace {

class ExpiringThreadBundle : public vespalib::ThreadBundle {
    std::atomic<vespalib::steady_time>& _now;
    vespalib::SimpleThreadBundle        _threads;

public:
    ExpiringThreadBundle(std::atomic<vespalib::steady_time>& now, size_t threads) : _now(now), _threads(threads) {}
    size_t size() const override { return _threads.size(); }
    void run(vespalib::Runnable* const* targets, size_t count) override {
        _now.store(vespalib::steady_time() + 2s);
        _threads.run(targets, count);
    }
};

TEST(RangeBitmapTimeoutTest, expiry_before_workers_run_stops_bitmap_construction) {
    Config config(BasicType::INT32, CollectionType::SINGLE);
    config.setFastSearch(true);
    auto attribute = test::AttributeBuilder("range", config).fill({1, 2, 3, 4, 5, 6}).get();
    for (size_t threads : {1, 3}) {
        SCOPED_TRACE(threads);
        std::atomic<vespalib::steady_time> now{vespalib::steady_time()};
        vespalib::Doom                     doom(now, vespalib::steady_time() + 1s);
        ExpiringThreadBundle               thread_bundle(now, threads);
        auto context = attribute->getSearch(std::make_unique<QueryTermSimple>("[2;5]", QueryTermSimple::Type::WORD),
                                            SearchContextParams());
        context->fetchPostings(queryeval::ExecuteInfo::create(1.0, doom, thread_bundle), true);
        fef::TermFieldMatchData match_data;
        auto                    iterator = context->createIterator(&match_data, true);
        queryeval::SimpleResult result;
        result.search(*iterator, attribute->getCommittedDocIdLimit());
        EXPECT_EQ(queryeval::SimpleResult(), result);
    }
    auto context = attribute->getSearch(std::make_unique<QueryTermSimple>("[2;5]", QueryTermSimple::Type::WORD),
                                        SearchContextParams());
    context->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    fef::TermFieldMatchData match_data;
    auto                    iterator = context->createIterator(&match_data, true);
    queryeval::SimpleResult result;
    result.search(*iterator, attribute->getCommittedDocIdLimit());
    EXPECT_EQ(queryeval::SimpleResult({2, 3, 4, 5}), result);
}

} // namespace
} // namespace search::attribute
