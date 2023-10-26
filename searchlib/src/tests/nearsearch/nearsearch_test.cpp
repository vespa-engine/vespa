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
#include <vespa/vespalib/testkit/testapp.h>

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

class Test : public vespalib::TestApp {
private:
    bool testNearSearch(MyQuery &query, uint32_t matchId);

public:
    int Main() override;
    void testBasicNear();
    void testRepeatedTerms();
};

int
Test::Main()
{
    TEST_INIT("nearsearch_test");

    testBasicNear();     TEST_FLUSH();
    testRepeatedTerms(); TEST_FLUSH();

    TEST_DONE();
}

TEST_APPHOOK(Test);

////////////////////////////////////////////////////////////////////////////////
//
// Tests
//
////////////////////////////////////////////////////////////////////////////////

void
Test::testBasicNear()
{
    MyTerm foo(UIntList().add(69),
               UIntList().add(6).add(11));
    for (uint32_t i = 0; i <= 1; ++i) {
        TEST_STATE(vespalib::make_string("i = %u", i).c_str());
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(foo), 69));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(foo), 69));
    }

    MyTerm bar(UIntList().add(68).add(69).add(70),
               UIntList().add(7).add(10));
    TEST_DO(testNearSearch(MyQuery(false, 0).addTerm(foo).addTerm(bar), 0));
    TEST_DO(testNearSearch(MyQuery(true,  0).addTerm(foo).addTerm(bar), 0));
    for (uint32_t i = 1; i <= 2; ++i) {
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(bar), 69));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(bar), 69));
    }

    MyTerm baz(UIntList().add(69).add(70).add(71),
               UIntList().add(8).add(9));
    for (uint32_t i = 0; i <= 1; ++i) {
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(bar).addTerm(baz), 0));
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(baz).addTerm(bar), 0));
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(bar).addTerm(baz).addTerm(foo), 0));
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(bar).addTerm(foo).addTerm(baz), 0));
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(baz).addTerm(foo).addTerm(bar), 0));
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(baz).addTerm(bar).addTerm(foo), 0));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(bar).addTerm(baz), 0));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(baz).addTerm(bar), 0));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(bar).addTerm(baz).addTerm(foo), 0));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(bar).addTerm(foo).addTerm(baz), 0));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(baz).addTerm(foo).addTerm(bar), 0));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(baz).addTerm(bar).addTerm(foo), 0));
    }
    for (uint32_t i = 2; i <= 3; ++i) {
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(bar).addTerm(baz), 69));
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(baz).addTerm(bar), 69));
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(bar).addTerm(baz).addTerm(foo), 69));
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(bar).addTerm(foo).addTerm(baz), 69));
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(baz).addTerm(foo).addTerm(bar), 69));
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(baz).addTerm(bar).addTerm(foo), 69));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(bar).addTerm(baz), 69));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(baz).addTerm(bar), 0));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(bar).addTerm(baz).addTerm(foo), 0));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(bar).addTerm(foo).addTerm(baz), 0));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(baz).addTerm(foo).addTerm(bar), 0));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(baz).addTerm(bar).addTerm(foo), 69));
    }
}

void
Test::testRepeatedTerms()
{
    MyTerm foo(UIntList().add(69),
               UIntList().add(1).add(2).add(3));
    TEST_DO(testNearSearch(MyQuery(false, 0).addTerm(foo).addTerm(foo), 69));
    TEST_DO(testNearSearch(MyQuery(true,  0).addTerm(foo).addTerm(foo), 0));
    for (uint32_t i = 1; i <= 2; ++i) {
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(foo), 69));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(foo), 69));
    }

    for (uint32_t i = 0; i <= 1; ++i) {
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(foo).addTerm(foo), 69));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(foo).addTerm(foo), 0));
    }
    for (uint32_t i = 2; i <= 3; ++i) {
        TEST_DO(testNearSearch(MyQuery(false, i).addTerm(foo).addTerm(foo).addTerm(foo), 69));
        TEST_DO(testNearSearch(MyQuery(true,  i).addTerm(foo).addTerm(foo).addTerm(foo), 69));
    }
}

bool
Test::testNearSearch(MyQuery &query, uint32_t matchId)
{
    LOG(info, "testNearSearch(%d)", matchId);
    search::queryeval::IntermediateBlueprint *near_b = 0;
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
    search::fef::MatchData::UP md(layout.createMatchData());

    bp->fetchPostings(search::queryeval::ExecuteInfo::TRUE);
    search::queryeval::SearchIterator::UP near = bp->createSearch(*md, true);
    near->initFullRange();
    bool foundMatch = false;
    for (near->seek(1u); ! near->isAtEnd(); near->seek(near->getDocId() + 1)) {
        uint32_t docId = near->getDocId();
        if (docId == matchId) {
            foundMatch = true;
        } else {
            LOG(info, "Document %d matched unexpectedly.", docId);
            return false;
        }
    }
    if (matchId == 0) {
        return EXPECT_TRUE(!foundMatch);
    } else {
        return EXPECT_TRUE(foundMatch);
    }
}
