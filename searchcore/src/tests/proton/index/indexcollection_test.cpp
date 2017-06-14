// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/fakesearchcontext.h>
#include <vespa/searchcorespi/index/warmupindexcollection.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("indexcollection_test");

using search::queryeval::ISourceSelector;
using search::queryeval::FakeSearchable;
using search::FixedSourceSelector;
using namespace proton;
using namespace searchcorespi;
using searchcorespi::index::WarmupConfig;

namespace {

class Test : public vespalib::TestApp,
             public IWarmupDone
{
    std::shared_ptr<ISourceSelector> _selector;
    std::shared_ptr<IndexSearchable> _source1;
    std::shared_ptr<IndexSearchable> _source2;
    std::shared_ptr<IndexSearchable> _fusion_source;
    vespalib::ThreadStackExecutor    _executor;
    std::shared_ptr<IndexSearchable> _warmup;

    void requireThatSearchablesCanBeAppended(IndexCollection::UP fsc);
    void requireThatSearchablesCanBeReplaced(IndexCollection::UP fsc);
    void requireThatReplaceAndRenumberUpdatesCollectionAfterFusion();
    IndexCollection::UP createWarmup(const IndexCollection::SP & prev, const IndexCollection::SP & next);
    virtual void warmupDone(ISearchableIndexCollection::SP current) override {
        (void) current;
    }

public:
    Test() : _selector(new FixedSourceSelector(0, "fs1")),
             _source1(new FakeIndexSearchable),
             _source2(new FakeIndexSearchable),
             _fusion_source(new FakeIndexSearchable),
             _executor(1, 128*1024),
             _warmup(new FakeIndexSearchable)
    {}
    ~Test() {}

    int Main() override;
};


IndexCollection::UP
Test::createWarmup(const IndexCollection::SP & prev, const IndexCollection::SP & next)
{
    return IndexCollection::UP(new WarmupIndexCollection(WarmupConfig(1.0, false), prev, next, *_warmup, _executor, *this));
}

int
Test::Main()
{
    TEST_INIT("indexcollection_test");

    TEST_DO(requireThatSearchablesCanBeAppended(IndexCollection::UP(new IndexCollection(_selector))));
    TEST_DO(requireThatSearchablesCanBeReplaced(IndexCollection::UP(new IndexCollection(_selector))));
    TEST_DO(requireThatReplaceAndRenumberUpdatesCollectionAfterFusion());
    {
        IndexCollection::SP prev(new IndexCollection(_selector));
        IndexCollection::SP next(new IndexCollection(_selector));
        requireThatSearchablesCanBeAppended(createWarmup(prev, next));
        EXPECT_EQUAL(0u, prev->getSourceCount());
        EXPECT_EQUAL(1u, next->getSourceCount());
    }
    {
        IndexCollection::SP prev(new IndexCollection(_selector));
        IndexCollection::SP next(new IndexCollection(_selector));
        requireThatSearchablesCanBeReplaced(createWarmup(prev, next));
        EXPECT_EQUAL(0u, prev->getSourceCount());
        EXPECT_EQUAL(1u, next->getSourceCount());
    }

    TEST_DONE();
}

void Test::requireThatSearchablesCanBeAppended(IndexCollection::UP fsc) {
    const uint32_t id = 42;

    fsc->append(id, _source1);
    EXPECT_EQUAL(1u, fsc->getSourceCount());
    EXPECT_EQUAL(id, fsc->getSourceId(0));
}

void Test::requireThatSearchablesCanBeReplaced(IndexCollection::UP fsc) {
    const uint32_t id = 42;

    fsc->append(id, _source1);
    EXPECT_EQUAL(1u, fsc->getSourceCount());
    EXPECT_EQUAL(id, fsc->getSourceId(0));
    EXPECT_EQUAL(_source1.get(), &fsc->getSearchable(0));

    fsc->replace(id, _source2);
    EXPECT_EQUAL(1u, fsc->getSourceCount());
    EXPECT_EQUAL(id, fsc->getSourceId(0));
    EXPECT_EQUAL(_source2.get(), &fsc->getSearchable(0));
}

void Test::requireThatReplaceAndRenumberUpdatesCollectionAfterFusion() {
    IndexCollection fsc(_selector);

    fsc.append(0, _source1);
    fsc.append(1, _source1);
    fsc.append(2, _source1);
    fsc.append(3, _source2);
    EXPECT_EQUAL(4u, fsc.getSourceCount());

    const uint32_t id_diff = 2;
    IndexCollection::UP new_fsc =
        IndexCollection::replaceAndRenumber(
                _selector, fsc, id_diff, _fusion_source);
    EXPECT_EQUAL(2u, new_fsc->getSourceCount());
    EXPECT_EQUAL(0u, new_fsc->getSourceId(0));
    EXPECT_EQUAL(_fusion_source.get(), &new_fsc->getSearchable(0));
    EXPECT_EQUAL(1u, new_fsc->getSourceId(1));
    EXPECT_EQUAL(_source2.get(), &new_fsc->getSearchable(1));
}

}  // namespace

TEST_APPHOOK(Test);
