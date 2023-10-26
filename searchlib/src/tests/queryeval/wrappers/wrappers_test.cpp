// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/filter_wrapper.h>
#include <vespa/searchlib/queryeval/booleanmatchiteratorwrapper.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/gtest/gtest.h>

#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/searchiteratorverifier.h>

using namespace search::fef;
using namespace search::queryeval;

struct ObservedData {
    uint32_t seekCnt;
    uint32_t unpackCnt;
    uint32_t dtorCnt;
    uint32_t unpackedDocId;
};

class WrapperTest : public ::testing::Test {
public:
    class DummyItr : public SearchIterator {
    private:
        ObservedData &_data;
        TermFieldMatchData *_match;
    public:
        DummyItr(ObservedData &data, TermFieldMatchData *m) : _data(data), _match(m) {}
        ~DummyItr() {
            ++_data.dtorCnt;
        }
        void doSeek(uint32_t docid) override {
            ++_data.seekCnt;
            if (docid <= 10) {
                setDocId(10);
            } else if (docid <= 20) {
                setDocId(20);
            } else {
                setAtEnd();
            }
        }
        void doUnpack(uint32_t docid) override {
            ++_data.unpackCnt;
            if (_match != 0) {
                _data.unpackedDocId = docid;
            }
        }
    };
    WrapperTest() : _data{0,0,0,0} {}
protected:
    ObservedData _data;

    void verify_unwrapped() {
        EXPECT_EQ(_data.seekCnt, 0u);
        EXPECT_EQ(_data.unpackCnt, 0u);
        EXPECT_EQ(_data.dtorCnt, 0u);

        // without wrapper
        TermFieldMatchData match;
        _data.unpackedDocId = 0;
        auto search = std::make_unique<DummyItr>(_data, &match);
        search->initFullRange();
        EXPECT_EQ(_data.unpackedDocId, 0u);
        EXPECT_TRUE(!search->seek(1u));
        EXPECT_EQ(search->getDocId(), 10u);
        EXPECT_TRUE(search->seek(10));
        search->unpack(10);
        EXPECT_EQ(_data.unpackedDocId, 10u);
        EXPECT_TRUE(!search->seek(15));
        EXPECT_EQ(search->getDocId(), 20u);
        EXPECT_TRUE(search->seek(20));
        search->unpack(20);
        EXPECT_EQ(_data.unpackedDocId, 20u);
        EXPECT_TRUE(!search->seek(25));
        EXPECT_TRUE(search->isAtEnd());

        search.reset(nullptr);
        EXPECT_EQ(_data.seekCnt, 3u);
        EXPECT_EQ(_data.unpackCnt, 2u);
        EXPECT_EQ(_data.dtorCnt, 1u);
    }
};

TEST_F(WrapperTest, filter_wrapper)
{
    verify_unwrapped();

    // with FilterWrapper
    TermFieldMatchData match;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&match);
    _data.unpackedDocId = 0;
    auto search = std::make_unique<FilterWrapper>(1);
search->wrap(std::make_unique<DummyItr>(_data, search->tfmda()[0]));
    search->initFullRange();
    EXPECT_EQ(_data.unpackedDocId, 0u);
    EXPECT_TRUE(!search->seek(1u));
    EXPECT_EQ(search->getDocId(), 10u);
    EXPECT_TRUE(search->seek(10));
    search->unpack(10);
    EXPECT_EQ(_data.unpackedDocId, 0u);
    EXPECT_TRUE(!search->seek(15));
    EXPECT_EQ(search->getDocId(), 20u);
    EXPECT_TRUE(search->seek(20));
    search->unpack(20);
    EXPECT_EQ(_data.unpackedDocId, 0u);
    EXPECT_TRUE(!search->seek(25));
    EXPECT_TRUE(search->isAtEnd());

    search.reset(nullptr);
    EXPECT_EQ(_data.seekCnt, 6u);
    EXPECT_EQ(_data.unpackCnt, 2u);
    EXPECT_EQ(_data.dtorCnt, 2u);
}

TEST_F(WrapperTest, boolean_match_iterator_wrapper)
{
    verify_unwrapped();
    { // with wrapper
        TermFieldMatchData match;
        TermFieldMatchDataArray tfmda;
        tfmda.add(&match);
        _data.unpackedDocId = 0;
        auto to_wrap = std::make_unique<DummyItr>(_data, &match);
        auto search = std::make_unique<BooleanMatchIteratorWrapper>(std::move(to_wrap), tfmda);
        search->initFullRange();
        EXPECT_EQ(_data.unpackedDocId, 0u);
        EXPECT_TRUE(!search->seek(1u));
        EXPECT_EQ(search->getDocId(), 10u);
        EXPECT_TRUE(search->seek(10));
        search->unpack(10);
        EXPECT_EQ(_data.unpackedDocId, 0u);
        EXPECT_TRUE(!search->seek(15));
        EXPECT_EQ(search->getDocId(), 20u);
        EXPECT_TRUE(search->seek(20));
        search->unpack(20);
        EXPECT_EQ(_data.unpackedDocId, 0u);
        EXPECT_TRUE(!search->seek(25));
        EXPECT_TRUE(search->isAtEnd());
    }
    EXPECT_EQ(_data.seekCnt, 6u);
    EXPECT_EQ(_data.unpackCnt, 2u);
    EXPECT_EQ(_data.dtorCnt, 2u);
    { // with wrapper, without match data

        auto to_wrap = std::make_unique<DummyItr>(_data, nullptr);
        auto search = std::make_unique<BooleanMatchIteratorWrapper>(std::move(to_wrap), TermFieldMatchDataArray());
        search->initFullRange();
        EXPECT_TRUE(!search->seek(1u));
        EXPECT_EQ(search->getDocId(), 10u);
        EXPECT_TRUE(search->seek(10));
        search->unpack(10);
        EXPECT_TRUE(!search->seek(15));
        EXPECT_EQ(search->getDocId(), 20u);
        EXPECT_TRUE(search->seek(20));
        search->unpack(20);
        EXPECT_TRUE(!search->seek(25));
        EXPECT_TRUE(search->isAtEnd());
    }
    EXPECT_EQ(_data.seekCnt, 9u);
    EXPECT_EQ(_data.unpackCnt, 2u);
    EXPECT_EQ(_data.dtorCnt, 3u);
}

class FilterWrapperVerifier : public search::test::SearchIteratorVerifier {
public:
    ~FilterWrapperVerifier() {}
    SearchIterator::UP create(bool strict) const override {
        auto search = std::make_unique<FilterWrapper>(1);
        search->wrap(createIterator(getExpectedDocIds(), strict));
        return search;
    }
};

TEST(FilterWrapperTest, adheres_to_search_iterator_requirements)
{
    FilterWrapperVerifier verifier;
    verifier.verify();
}

class BooleanMatchIteratorWrapperVerifier : public search::test::SearchIteratorVerifier {
public:
    SearchIterator::UP create(bool strict) const override {
        return std::make_unique<BooleanMatchIteratorWrapper>(createIterator(getExpectedDocIds(), strict), _tfmda);
    }
    ~BooleanMatchIteratorWrapperVerifier() {}
private:
    mutable TermFieldMatchDataArray _tfmda;
};

TEST(BooleanMatchIteratorWrapperWrapperTest, adheres_to_search_iterator_requirements)
{
    BooleanMatchIteratorWrapperVerifier verifier;
    verifier.verify();
}

GTEST_MAIN_RUN_ALL_TESTS()
