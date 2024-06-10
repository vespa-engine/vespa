// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("nearsearch_test");

#include <vespa/searchlib/common/resultset.h>
#include <vespa/searchlib/queryeval/nearsearch.h>
#include <vespa/searchlib/queryeval/searchable.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <set>
#include <vespa/vespalib/gtest/gtest.h>

////////////////////////////////////////////////////////////////////////////////
//
// Utilities
//
////////////////////////////////////////////////////////////////////////////////

class UIntList : public std::set<uint32_t> {
public:
    UIntList &add(uint32_t i) {
        std::set<uint32_t>::insert(i);
        return *this;
    }
};

class MyTerm {
private:
    std::set<uint32_t> _docs;
    std::set<uint32_t> _data;

public:
    MyTerm(const std::set<uint32_t> &doc, const std::set<uint32_t> &pos);
    ~MyTerm();

    search::queryeval::Blueprint::UP
    make_blueprint(uint32_t fieldId, search::fef::TermFieldHandle handle) const
    {
        search::queryeval::FakeResult result;
        for (std::set<uint32_t>::const_iterator doc = _docs.begin();
             doc != _docs.end(); ++doc)
        {
            result.doc(*doc);
            for (std::set<uint32_t>::const_iterator pos = _data.begin();
                 pos != _data.end(); ++pos)
            {
                result.pos(*pos);
            }
        }
        return search::queryeval::Blueprint::UP(
                new search::queryeval::FakeBlueprint(
                        search::queryeval::FieldSpec("<field>", fieldId, handle),
                        result));
    }
};

MyTerm::MyTerm(const std::set<uint32_t> &doc, const std::set<uint32_t> &pos)
    : _docs(doc),
      _data(pos)
{}
MyTerm::~MyTerm() = default;

class MyQuery {
private:
    std::vector<MyTerm*> _terms;
    bool                 _ordered;
    uint32_t             _window;

public:
    MyQuery(bool ordered, uint32_t window);
    ~MyQuery();

    MyQuery &addTerm(MyTerm &term) {
        _terms.push_back(&term);
        return *this;
    }

    uint32_t getNumTerms() const {
        return _terms.size();
    }

    MyTerm &getTerm(uint32_t i) {
        return *_terms[i];
    }

    bool isOrdered() const {
        return _ordered;
    }

    uint32_t getWindow() const {
        return _window;
    }
};

MyQuery::MyQuery(bool ordered, uint32_t window)
    : _terms(),
      _ordered(ordered),
      _window(window)
{}
MyQuery::~MyQuery() {}

////////////////////////////////////////////////////////////////////////////////
//
// Setup
//
////////////////////////////////////////////////////////////////////////////////

class NearSearchTest : public ::testing::Test {
protected:
    void testNearSearch(MyQuery &query, uint32_t matchId, const vespalib::string& label);

    NearSearchTest();
    ~NearSearchTest() override;
};

NearSearchTest::NearSearchTest()
    : ::testing::Test()
{
}

NearSearchTest::~NearSearchTest() = default;

////////////////////////////////////////////////////////////////////////////////
//
// Tests
//
////////////////////////////////////////////////////////////////////////////////

TEST_F(NearSearchTest, basic_near)
{
    MyTerm foo(UIntList().add(69),
               UIntList().add(6).add(11));
    for (uint32_t i = 0; i <= 1; ++i) {
        SCOPED_TRACE(vespalib::make_string("i = %u", i));
        testNearSearch(MyQuery(false, i).addTerm(foo), 69, "near 1");
        testNearSearch(MyQuery(true,  i).addTerm(foo), 69, "onear 1");
    }

    MyTerm bar(UIntList().add(68).add(69).add(70),
               UIntList().add(7).add(10));
    testNearSearch(MyQuery(false, 0).addTerm(foo).addTerm(bar), 0, "near 2");
    testNearSearch(MyQuery(true,  0).addTerm(foo).addTerm(bar), 0, "onear 2");
    for (uint32_t i = 1; i <= 2; ++i) {
        SCOPED_TRACE(vespalib::make_string("i = %u", i));
        testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(bar), 69, "near 3");
        testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(bar), 69, "onear 3");
    }

    MyTerm baz(UIntList().add(69).add(70).add(71),
               UIntList().add(8).add(9));
    for (uint32_t i = 0; i <= 1; ++i) {
        SCOPED_TRACE(vespalib::make_string("i = %u", i));
        testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(bar).addTerm(baz), 0, "near 10");
        testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(baz).addTerm(bar), 0, "near 11");
        testNearSearch(MyQuery(false, i).addTerm(bar).addTerm(baz).addTerm(foo), 0, "near 12");
        testNearSearch(MyQuery(false, i).addTerm(bar).addTerm(foo).addTerm(baz), 0, "near 13");
        testNearSearch(MyQuery(false, i).addTerm(baz).addTerm(foo).addTerm(bar), 0, "near 14");
        testNearSearch(MyQuery(false, i).addTerm(baz).addTerm(bar).addTerm(foo), 0, "near 15");
        testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(bar).addTerm(baz), 0, "onear 10");
        testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(baz).addTerm(bar), 0, "onear 11");
        testNearSearch(MyQuery(true,  i).addTerm(bar).addTerm(baz).addTerm(foo), 0, "onear 12");
        testNearSearch(MyQuery(true,  i).addTerm(bar).addTerm(foo).addTerm(baz), 0, "onear 13");
        testNearSearch(MyQuery(true,  i).addTerm(baz).addTerm(foo).addTerm(bar), 0, "onear 14");
        testNearSearch(MyQuery(true,  i).addTerm(baz).addTerm(bar).addTerm(foo), 0, "onear 15");
    }
    for (uint32_t i = 2; i <= 3; ++i) {
        SCOPED_TRACE(vespalib::make_string("i = %u", i));
        testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(bar).addTerm(baz), 69, "near 20");
        testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(baz).addTerm(bar), 69, "near 21");
        testNearSearch(MyQuery(false, i).addTerm(bar).addTerm(baz).addTerm(foo), 69, "near 22");
        testNearSearch(MyQuery(false, i).addTerm(bar).addTerm(foo).addTerm(baz), 69, "near 23");
        testNearSearch(MyQuery(false, i).addTerm(baz).addTerm(foo).addTerm(bar), 69, "near 24");
        testNearSearch(MyQuery(false, i).addTerm(baz).addTerm(bar).addTerm(foo), 69, "near 25");
        testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(bar).addTerm(baz), 69, "onear 20");
        testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(baz).addTerm(bar), 0, "onear 21");
        testNearSearch(MyQuery(true,  i).addTerm(bar).addTerm(baz).addTerm(foo), 0, "onear 22");
        testNearSearch(MyQuery(true,  i).addTerm(bar).addTerm(foo).addTerm(baz), 0, "onear 23");
        testNearSearch(MyQuery(true,  i).addTerm(baz).addTerm(foo).addTerm(bar), 0, "onear 24");
        testNearSearch(MyQuery(true,  i).addTerm(baz).addTerm(bar).addTerm(foo), 69, "onear 25");
    }
}


TEST_F(NearSearchTest, repeated_terms)
{
    MyTerm foo(UIntList().add(69),
               UIntList().add(1).add(2).add(3));
    testNearSearch(MyQuery(false, 0).addTerm(foo).addTerm(foo), 69, "near 50");
    testNearSearch(MyQuery(true,  0).addTerm(foo).addTerm(foo), 0, "onear 50");
    for (uint32_t i = 1; i <= 2; ++i) {
        SCOPED_TRACE(vespalib::make_string("i = %u", i));
        testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(foo), 69, "near 51");
        testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(foo), 69, "onear 51");
    }

    for (uint32_t i = 0; i <= 1; ++i) {
        SCOPED_TRACE(vespalib::make_string("i = %u", i));
        testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(foo).addTerm(foo), 69, "near 52");
        testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(foo).addTerm(foo), 0, "onear 52");
    }
    for (uint32_t i = 2; i <= 3; ++i) {
        SCOPED_TRACE(vespalib::make_string("i = %u", i));
        testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(foo).addTerm(foo), 69, "near 53");
        testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(foo).addTerm(foo), 69, "onear 53");
    }
}

void
NearSearchTest::testNearSearch(MyQuery &query, uint32_t matchId, const vespalib::string& label)
{
    SCOPED_TRACE(vespalib::make_string("%s - %u", label.c_str(), matchId));
    search::queryeval::IntermediateBlueprint *near_b = nullptr;
    if (query.isOrdered()) {
        near_b = new search::queryeval::ONearBlueprint(query.getWindow());
    } else {
        near_b = new search::queryeval::NearBlueprint(query.getWindow());
    }
    search::queryeval::Blueprint::UP bp(near_b);
    search::fef::MatchDataLayout layout;
    for (uint32_t i = 0; i < query.getNumTerms(); ++i) {
        uint32_t fieldId = 0;
        layout.allocTermField(fieldId);
        near_b->addChild(query.getTerm(i).make_blueprint(fieldId, i));
    }
    bp->setDocIdLimit(1000);
    bp = search::queryeval::Blueprint::optimize_and_sort(std::move(bp));
    bp->fetchPostings(search::queryeval::ExecuteInfo::FULL);
    search::fef::MatchData::UP md(layout.createMatchData());
    search::queryeval::SearchIterator::UP near = bp->createSearch(*md);
    near->initFullRange();
    bool foundMatch = false;
    for (near->seek(1u); ! near->isAtEnd(); near->seek(near->getDocId() + 1)) {
        uint32_t docId = near->getDocId();
        if (docId == matchId) {
            foundMatch = true;
        } else {
            FAIL() << "Document " << docId << " matched unexpectedly.";
        }
    }
    if (matchId == 0) {
        EXPECT_TRUE(!foundMatch);
    } else {
        EXPECT_TRUE(foundMatch);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
