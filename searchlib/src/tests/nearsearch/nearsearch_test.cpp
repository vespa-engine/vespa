// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/nearsearch.h>
//#include <vespa/searchlib/common/resultset.h>
#include <vespa/searchlib/queryeval/searchable.h>
#include <vespa/searchlib/queryeval/i_element_gap_inspector.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/test/mock_element_gap_inspector.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("nearsearch_test");

using search::queryeval::IElementGapInspector;
using search::queryeval::test::MockElementGapInspector;

////////////////////////////////////////////////////////////////////////////////
//
// Utilities
//
////////////////////////////////////////////////////////////////////////////////

class ElementPositions {
    uint32_t _id;
    uint32_t _len;
    std::vector<uint32_t> _pos;
public:
    ElementPositions(uint32_t id, uint32_t len, const std::vector<uint32_t>& pos)
        : _id(id),
          _len(len),
          _pos(pos)
    {
    }
    uint32_t get_id() const noexcept { return _id; }
    uint32_t get_len() const noexcept { return _len; }
    const std::vector<uint32_t>& get_pos() const noexcept { return _pos; }
};

class MyTerm {
private:
    std::vector<uint32_t> _docs;
    std::vector<ElementPositions> _epos;

public:
    MyTerm(const std::vector<uint32_t>& doc, const std::vector<ElementPositions>& epos);
    ~MyTerm();

    search::queryeval::Blueprint::UP
    make_blueprint(uint32_t fieldId, search::fef::TermFieldHandle handle) const
    {
        search::queryeval::FakeResult result;
        for (auto docid : _docs) {
            result.doc(docid);
            for (auto& epos : _epos) {
                result.elem(epos.get_id());
                result.len(epos.get_len());
                for (auto pos : epos.get_pos()) {
                    result.pos(pos);
                }
            }
        }
        return search::queryeval::Blueprint::UP(
                new search::queryeval::FakeBlueprint(
                        search::queryeval::FieldSpec("<field>", fieldId, handle),
                        result));
    }
};

MyTerm::MyTerm(const std::vector<uint32_t>& doc, const std::vector<ElementPositions>& epos)
    : _docs(doc),
      _epos(epos)
{}

MyTerm::~MyTerm() = default;

class MyQuery {
private:
    std::vector<MyTerm*> _terms;
    bool                 _ordered;
    uint32_t             _window;
    MockElementGapInspector _element_gap_inspector;

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
    const IElementGapInspector& get_element_gap_inspector() const noexcept { return _element_gap_inspector; }
    MyQuery& set_element_gap(std::optional<uint32_t> element_gap) {
        _element_gap_inspector = MockElementGapInspector(element_gap);
        return *this;
    }
};

MyQuery::MyQuery(bool ordered, uint32_t window)
    : _terms(),
      _ordered(ordered),
      _window(window),
      _element_gap_inspector(std::nullopt)
{}

MyQuery::~MyQuery() = default;

////////////////////////////////////////////////////////////////////////////////
//
// Setup
//
////////////////////////////////////////////////////////////////////////////////

class NearSearchTest : public ::testing::Test {
protected:
    void testNearSearch(MyQuery &query, uint32_t matchId, const std::string& label);

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
    MyTerm foo({69}, {{0, 100, {6, 11}}});
    for (uint32_t i = 0; i <= 1; ++i) {
        SCOPED_TRACE(vespalib::make_string("i = %u", i));
        testNearSearch(MyQuery(false, i).addTerm(foo), 69, "near 1");
        testNearSearch(MyQuery(true,  i).addTerm(foo), 69, "onear 1");
    }

    MyTerm bar({68, 69, 70}, {{0, 100, {7, 10}}});
    testNearSearch(MyQuery(false, 0).addTerm(foo).addTerm(bar), 0, "near 2");
    testNearSearch(MyQuery(true,  0).addTerm(foo).addTerm(bar), 0, "onear 2");
    for (uint32_t i = 1; i <= 2; ++i) {
        SCOPED_TRACE(vespalib::make_string("i = %u", i));
        testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(bar), 69, "near 3");
        testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(bar), 69, "onear 3");
    }

    MyTerm baz({69, 70, 71}, {{0, 100, {8, 9}}});
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

TEST_F(NearSearchTest, element_boundary)
{
    MyTerm foo({69}, {{0, 5, {0}}});
    MyTerm bar({69, 70, 71}, {{1, 5, {1}}});
    testNearSearch(MyQuery(false, 20).addTerm(foo).addTerm(bar), 0, "near 1");
    testNearSearch(MyQuery(true, 20).addTerm(foo).addTerm(bar), 0, "onear 1");
    testNearSearch(MyQuery(false, 20).addTerm(foo).addTerm(bar).set_element_gap(0), 69, "near 1");
    testNearSearch(MyQuery(true, 20).addTerm(foo).addTerm(bar).set_element_gap(0), 69, "onear 1");
    testNearSearch(MyQuery(false, 20).addTerm(foo).addTerm(bar).set_element_gap(14), 69, "near 2");
    testNearSearch(MyQuery(true, 20).addTerm(foo).addTerm(bar).set_element_gap(14), 69, "onear 2");
    testNearSearch(MyQuery(false, 20).addTerm(foo).addTerm(bar).set_element_gap(15), 0, "near 3");
    testNearSearch(MyQuery(true, 20).addTerm(foo).addTerm(bar).set_element_gap(15), 0, "onear 3");
}

TEST_F(NearSearchTest, repeated_terms)
{
    MyTerm foo({69},{{0, 100, {1, 2, 3}}});
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
NearSearchTest::testNearSearch(MyQuery &query, uint32_t matchId, const std::string& label)
{
    SCOPED_TRACE(vespalib::make_string("%s - %u", label.c_str(), matchId));
    search::queryeval::IntermediateBlueprint *near_b = nullptr;
    if (query.isOrdered()) {
        near_b = new search::queryeval::ONearBlueprint(query.getWindow(), query.get_element_gap_inspector());
    } else {
        near_b = new search::queryeval::NearBlueprint(query.getWindow(), query.get_element_gap_inspector());
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
