// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/searchcontextelementiterator.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/queryeval/fake_search.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#

#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("searchcontextelementiterator_test");

using namespace search::attribute;
using namespace search;

namespace {

AttributeVector::SP
createAndFillAttribute() {
    AttributeFactory factory;
    AttributeVector::SP attribute = factory.createAttribute("mva", Config(BasicType::INT32, CollectionType::ARRAY));
    attribute->addDocs(6);
    IntegerAttribute & ia = dynamic_cast<IntegerAttribute &>(*attribute);
    ia.append(1, 3, 1);
    for (int v : {1,2,3,1,2,3}) {
        ia.append(2, v, 1);
    }
    for (int v : {1,2,3,4,5,1,2,3,4,5,6}) {
        ia.append(4, v, 1);
    }
    ia.append(5, 5, 1);
    attribute->commit();
    return attribute;
}

queryeval::FakeResult
createResult() {
    queryeval::FakeResult result;
    result.doc(2).elem(0).pos(7).pos(9)
                        .elem(3).pos(1);
    result.doc(4).elem(0).pos(2)
                        .elem(5).pos(1).pos(2).pos(3);
    return result;
}

void
verifySeek(queryeval::ElementIterator & elemIt) {
    elemIt.initFullRange();
    EXPECT_FALSE(elemIt.seek(1));
    EXPECT_TRUE(elemIt.seek(2));
    EXPECT_FALSE(elemIt.seek(3));
    EXPECT_TRUE(elemIt.seek(4));
    EXPECT_FALSE(elemIt.seek(5));
}

void
verifyGetElementIds(queryeval::ElementIterator & elemIt, const std::vector<std::vector<uint32_t>> & expectedALL) {
    elemIt.initFullRange();
    std::vector<uint32_t> elems;
    for (uint32_t docId : {1,2,3,4,5}) {
        const auto & expected = expectedALL[docId];
        elems.clear();
        EXPECT_EQ(expected.empty(), !elemIt.seek(docId));
        assert(expected.empty() != elemIt.seek(docId));
        if (elemIt.seek(docId)) {
            elemIt.getElementIds(docId, elems);
            EXPECT_EQ(expected.size(), elems.size());
            EXPECT_EQ(expected, elems);
        }
    }
}

void
verifyMergeElementIds(queryeval::ElementIterator & elemIt, std::vector<uint32_t> initial, const std::vector<std::vector<uint32_t>> & expectedALL) {
    elemIt.initFullRange();
    std::vector<uint32_t> elems;
    for (uint32_t docId : {1,2,3,4,5}) {
        const auto & expected = expectedALL[docId];
        elems = initial;
        if (elemIt.seek(docId)) {
            elemIt.mergeElementIds(docId, elems);
            EXPECT_EQ(expected.size(), elems.size());
            EXPECT_EQ(expected, elems);
        }
    }
}

void
verifyElementIterator(queryeval::ElementIterator & elemIt) {
    verifySeek(elemIt);
    std::vector<std::vector<uint32_t>> expectedALL = {{}, {}, {0, 3}, {}, {0, 5}, {}};
    std::vector<std::vector<uint32_t>> expectedNONE = {{}, {}, {}, {}, {}, {}};
    std::vector<std::vector<uint32_t>> expectedSOME = {{}, {}, {3}, {}, {5}, {}};
    verifyGetElementIds(elemIt, expectedALL);
    verifyMergeElementIds(elemIt, {0,1,2,3,4,5}, expectedALL);
    verifyMergeElementIds(elemIt, {}, expectedNONE);
    verifyMergeElementIds(elemIt, {1,3,4,5}, expectedSOME);
}

}

TEST(ElementIteratorTest, require_that_searchcontext)
{
    AttributeVector::SP attribute = createAndFillAttribute();
    fef::TermFieldMatchData tfmd;

    SearchContextParams params;
    ISearchContext::UP sc = attribute->createSearchContext(std::make_unique<QueryTermSimple>("1", QueryTermSimple::Type::WORD), params);
    SearchContextElementIterator elemIt(sc->createIterator(&tfmd, false), *sc);
    verifyElementIterator(elemIt);
}

TEST(ElementIteratorTest, require_that_non_searchcontext)
{
    fef::TermFieldMatchData tfmd;
    fef::TermFieldMatchDataArray tfmda;
    tfmda.add(&tfmd);
    queryeval::FakeResult result = createResult();
    auto search = std::make_unique<queryeval::FakeSearch>("","","", result, tfmda);
    queryeval::ElementIteratorWrapper wrapper(std::move(search), tfmd);
    verifyElementIterator(wrapper);
}

GTEST_MAIN_RUN_ALL_TESTS()
