// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/fakesearchcontext.h>
#include <vespa/searchcorespi/index/warmupindexcollection.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("indexcollection_test");

using namespace proton;
using namespace searchcorespi;
using search::FixedSourceSelector;
using search::index::FieldLengthInfo;
using search::queryeval::FakeSearchable;
using search::queryeval::ISourceSelector;
using searchcorespi::index::WarmupConfig;

class MockIndexSearchable : public FakeIndexSearchable {
private:
    FieldLengthInfo _field_length_info;

public:
    MockIndexSearchable()
        : _field_length_info()
    {}
    MockIndexSearchable(const FieldLengthInfo& field_length_info)
        : _field_length_info(field_length_info)
    {}
    FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const override {
        (void) field_name;
        return _field_length_info;
    }
};

class IndexCollectionTest : public ::testing::Test,
                            public IWarmupDone
{
public:
    std::shared_ptr<ISourceSelector> _selector;
    std::shared_ptr<IndexSearchable> _source1;
    std::shared_ptr<IndexSearchable> _source2;
    std::shared_ptr<IndexSearchable> _fusion_source;
    vespalib::ThreadStackExecutor    _executor;
    std::shared_ptr<IndexSearchable> _warmup;

    void expect_searchable_can_be_appended(IndexCollection::UP collection) {
        const uint32_t id = 42;

        collection->append(id, _source1);
        EXPECT_EQ(1u, collection->getSourceCount());
        EXPECT_EQ(id, collection->getSourceId(0));
    }

    void expect_searchable_can_be_replaced(IndexCollection::UP collection) {
        const uint32_t id = 42;

        collection->append(id, _source1);
        EXPECT_EQ(1u, collection->getSourceCount());
        EXPECT_EQ(id, collection->getSourceId(0));
        EXPECT_EQ(_source1.get(), &collection->getSearchable(0));

        collection->replace(id, _source2);
        EXPECT_EQ(1u, collection->getSourceCount());
        EXPECT_EQ(id, collection->getSourceId(0));
        EXPECT_EQ(_source2.get(), &collection->getSearchable(0));
    }

    IndexCollection::UP make_unique_collection() const {
        return std::make_unique<IndexCollection>(_selector);
    }

    IndexCollection::SP make_shared_collection() const {
        return std::make_shared<IndexCollection>(_selector);
    }

    IndexCollection::UP create_warmup(const IndexCollection::SP& prev, const IndexCollection::SP& next) {
        return std::make_unique<WarmupIndexCollection>(WarmupConfig(1s, false), prev, next, *_warmup, _executor, *this);
    }

    virtual void warmupDone(ISearchableIndexCollection::SP current) override {
        (void) current;
    }

    IndexCollectionTest()
        : _selector(new FixedSourceSelector(0, "fs1")),
          _source1(new MockIndexSearchable({3, 5})),
          _source2(new MockIndexSearchable({7, 11})),
          _fusion_source(new FakeIndexSearchable),
          _executor(1, 128_Ki),
          _warmup(new FakeIndexSearchable)
    {}
    ~IndexCollectionTest() = default;
};


TEST_F(IndexCollectionTest, searchable_can_be_appended_to_normal_collection)
{
    expect_searchable_can_be_appended(make_unique_collection());
}

TEST_F(IndexCollectionTest, searchable_can_be_replaced_in_normal_collection)
{
    expect_searchable_can_be_replaced(make_unique_collection());
}

TEST_F(IndexCollectionTest, searchable_can_be_appended_to_warmup_collection)
{
    auto prev = make_shared_collection();
    auto next = make_shared_collection();
    expect_searchable_can_be_appended(create_warmup(prev, next));
    EXPECT_EQ(0u, prev->getSourceCount());
    EXPECT_EQ(1u, next->getSourceCount());
}

TEST_F(IndexCollectionTest, searchable_can_be_replaced_in_warmup_collection)
{
    auto prev = make_shared_collection();
    auto next = make_shared_collection();
    expect_searchable_can_be_replaced(create_warmup(prev, next));
    EXPECT_EQ(0u, prev->getSourceCount());
    EXPECT_EQ(1u, next->getSourceCount());
}

TEST_F(IndexCollectionTest, replace_and_renumber_updates_collection_after_fusion)
{
    IndexCollection fsc(_selector);

    fsc.append(0, _source1);
    fsc.append(1, _source1);
    fsc.append(2, _source1);
    fsc.append(3, _source2);
    EXPECT_EQ(4u, fsc.getSourceCount());

    const uint32_t id_diff = 2;
    auto new_fsc = IndexCollection::replaceAndRenumber(_selector, fsc, id_diff, _fusion_source);
    EXPECT_EQ(2u, new_fsc->getSourceCount());
    EXPECT_EQ(0u, new_fsc->getSourceId(0));
    EXPECT_EQ(_fusion_source.get(), &new_fsc->getSearchable(0));
    EXPECT_EQ(1u, new_fsc->getSourceId(1));
    EXPECT_EQ(_source2.get(), &new_fsc->getSearchable(1));
}

TEST_F(IndexCollectionTest, returns_field_length_info_for_last_added_searchable)
{
    auto collection = make_unique_collection();

    collection->append(3, _source1);
    collection->append(4, _source2);

    EXPECT_DOUBLE_EQ(7, collection->get_field_length_info("foo").get_average_field_length());
    EXPECT_EQ(11, collection->get_field_length_info("foo").get_num_samples());
}

TEST_F(IndexCollectionTest, returns_empty_field_length_info_when_no_searchables_exists)
{
    auto collection = make_unique_collection();

    EXPECT_DOUBLE_EQ(0, collection->get_field_length_info("foo").get_average_field_length());
    EXPECT_EQ(0, collection->get_field_length_info("foo").get_num_samples());
}

GTEST_MAIN_RUN_ALL_TESTS()
