// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/attribute/attribute_blueprint_factory.h>
#include <vespa/searchlib/attribute/attribute_weighted_set_blueprint.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/attribute/singlestringattribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/fake_result.h>
#include <vespa/searchlib/queryeval/weighted_set_term_search.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/searchlib/attribute/enumstore.hpp>
#include <vespa/searchcommon/attribute/config.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_weighted_set_blueprint_test");

using namespace search;
using namespace search::query;
using namespace search::fef;
using namespace search::queryeval;
using namespace search::attribute;
using namespace search::attribute::test;

namespace {

void
setupAttributeManager(MockAttributeManager &manager, bool isFilter)
{
    AttributeVector::DocId docId;
    {
        AttributeVector::SP attr_sp = AttributeFactory::createAttribute("integer", Config(BasicType("int64")).setIsFilter(isFilter));
        manager.addAttribute(attr_sp);

        auto *attr = (IntegerAttribute*)(attr_sp.get());
        for (size_t i = 1; i < 10; ++i) {
            attr->addDoc(docId);
            assert(i == docId);
            attr->update(docId, i);
            attr->commit();
        }
    }
    {
        AttributeVector::SP attr_sp = AttributeFactory::createAttribute("string", Config(BasicType("string")).setIsFilter(isFilter));
        manager.addAttribute(attr_sp);

        auto *attr = (StringAttribute*)(attr_sp.get());
        for (size_t i = 1; i < 10; ++i) {
            attr->addDoc(docId);
            assert(i == docId);
            attr->update(i, std::string(1, '1' + i - 1).c_str());
            attr->commit();
        }
    }
    {
        AttributeVector::SP attr_sp = AttributeFactory::createAttribute(
                "multi", Config(BasicType("int64"), search::attribute::CollectionType("array")).setIsFilter(isFilter));
        manager.addAttribute(attr_sp);
        auto *attr = (IntegerAttribute*)(attr_sp.get());
        for (size_t i = 1; i < 10; ++i) {
            attr->addDoc(docId);
            assert(i == docId);
            attr->append(docId, i, 0);
            attr->append(docId, i + 10, 1);
            attr->commit();
        }
    }
}

struct WS {
    static const uint32_t fieldId = 42;
    IAttributeManager & attribute_manager;
    MatchDataLayout layout;
    TermFieldHandle handle;
    std::vector<std::pair<std::string, uint32_t> > tokens;

    explicit WS(IAttributeManager & manager)
        : attribute_manager(manager),
          layout(), handle(layout.allocTermField(fieldId)),
          tokens()
    {
        MatchData::UP tmp = layout.createMatchData();
        ASSERT_TRUE(tmp->resolveTermField(handle)->getFieldId() == fieldId);
    }

    WS &add(const std::string &token, uint32_t weight) {
        tokens.emplace_back(token, weight);
        return *this;
    }

    Node::UP createNode() const {
        auto *node = new SimpleWeightedSetTerm(tokens.size(), "view", 0, Weight(0));
        for (const auto & token : tokens) {
            node->addTerm(token.first, Weight(token.second));
        }
        return Node::UP(node);
    }

    SearchIterator::UP
    createSearch(Searchable &searchable, const std::string &field, bool strict) const {
        AttributeContext ac(attribute_manager);
        FakeRequestContext requestContext(&ac);
        MatchData::UP md = layout.createMatchData();
        Node::UP node = createNode();
        FieldSpecList fields;
        fields.add(FieldSpec(field, fieldId, handle, ac.getAttribute(field)->getIsFilter()));
        queryeval::Blueprint::UP bp = searchable.createBlueprint(requestContext, fields, *node);
        bp->fetchPostings(queryeval::ExecuteInfo::createForTest(strict));
        SearchIterator::UP sb = bp->createSearch(*md, strict);
        return sb;
    }
    bool isWeightedSetTermSearch(Searchable &searchable, const std::string &field, bool strict) const {
        return dynamic_cast<WeightedSetTermSearch *>(createSearch(searchable, field, strict).get()) != nullptr;
    }

    FakeResult search(Searchable &searchable, const std::string &field, bool strict) const {
        AttributeContext ac(attribute_manager);
        FakeRequestContext requestContext(&ac);
        MatchData::UP md = layout.createMatchData();
        Node::UP node = createNode();
        FieldSpecList fields;
        fields.add(FieldSpec(field, fieldId, handle));
        queryeval::Blueprint::UP bp = searchable.createBlueprint(requestContext, fields, *node);
        bp->fetchPostings(queryeval::ExecuteInfo::createForTest(strict));
        SearchIterator::UP sb = bp->createSearch(*md, strict);
        FakeResult result;
        sb->initRange(1, 10);
        for (uint32_t docId = 1; docId < 10; ++docId) {
            if (sb->seek(docId)) {
                sb->unpack(docId);
                result.doc(docId);
                TermFieldMatchData &data = *md->resolveTermField(handle);
                FieldPositionsIterator itr = data.getIterator();
                for (; itr.valid(); itr.next()) {
                    result.elem(itr.getElementId());
                    result.weight(itr.getElementWeight());
                    result.pos(itr.getPosition());
                }
            }
        }
        return result;
    }
};

} // namespace <unnamed>

void test_tokens(bool isFilter, const std::vector<uint32_t> & docs) {
    MockAttributeManager manager;
    setupAttributeManager(manager, isFilter);
    AttributeBlueprintFactory adapter;

    FakeResult expect = FakeResult();
    WS ws = WS(manager);
    for (uint32_t doc : docs) {
        auto docS = vespalib::stringify(doc);
        int32_t weight = doc * 10;
        expect.doc(doc).weight(weight).pos(0);
        ws.add(docS, weight);
    }

    EXPECT_TRUE(ws.isWeightedSetTermSearch(adapter, "integer", true));
    EXPECT_TRUE(!ws.isWeightedSetTermSearch(adapter, "integer", false));
    EXPECT_TRUE(ws.isWeightedSetTermSearch(adapter, "string", true));
    EXPECT_TRUE(!ws.isWeightedSetTermSearch(adapter, "string", false));
    EXPECT_TRUE(ws.isWeightedSetTermSearch(adapter, "multi", true));
    EXPECT_TRUE(ws.isWeightedSetTermSearch(adapter, "multi", false));

    EXPECT_EQUAL(expect, ws.search(adapter, "integer", true));
    EXPECT_EQUAL(expect, ws.search(adapter, "integer", false));
    EXPECT_EQUAL(expect, ws.search(adapter, "string", true));
    EXPECT_EQUAL(expect, ws.search(adapter, "string", false));
    EXPECT_EQUAL(expect, ws.search(adapter, "multi", true));
    EXPECT_EQUAL(expect, ws.search(adapter, "multi", false));
}
TEST("attribute_weighted_set_test") {
    test_tokens(false, {3, 5, 7});
    test_tokens(true, {3, 5, 7});
    test_tokens(false, {3});
}

namespace {

void
normalize_class_name_helper(vespalib::string& class_name, const vespalib::string& old, const vespalib::string& replacement)
{
    for (;;) {
        auto pos = class_name.find(old);
        if (pos == vespalib::string::npos) {
            break;
        }
        class_name.replace(pos, old.size(), replacement);
    }
}

vespalib::string normalize_class_name(vespalib::string class_name)
{
    normalize_class_name_helper(class_name, "long long", "long");
    normalize_class_name_helper(class_name, ">>", "> >");
    return class_name;
}

}

TEST("attribute_weighted_set_single_token_filter_lifted_out") {
    MockAttributeManager manager;
    setupAttributeManager(manager, true);
    AttributeBlueprintFactory adapter;

    FakeResult expect = FakeResult().doc(3).elem(0).weight(30).pos(0);
    WS ws = WS(manager).add("3", 30);

    EXPECT_EQUAL("search::FilterAttributeIteratorStrict<search::attribute::SingleNumericSearchContext<long, search::attribute::NumericMatcher<long> > >",
                 normalize_class_name(ws.createSearch(adapter, "integer", true)->getClassName()));
    EXPECT_EQUAL("search::FilterAttributeIteratorT<search::attribute::SingleNumericSearchContext<long, search::attribute::NumericMatcher<long> > >",
                 normalize_class_name(ws.createSearch(adapter, "integer", false)->getClassName()));
    EXPECT_EQUAL("search::FilterAttributeIteratorStrict<search::attribute::SingleEnumSearchContext<char const*, search::attribute::StringSearchContext> >",
                 normalize_class_name(ws.createSearch(adapter, "string", true)->getClassName()));
    EXPECT_EQUAL("search::FilterAttributeIteratorT<search::attribute::SingleEnumSearchContext<char const*, search::attribute::StringSearchContext> >",
                 normalize_class_name(ws.createSearch(adapter, "string", false)->getClassName()));
    EXPECT_TRUE(ws.isWeightedSetTermSearch(adapter, "multi", true));
    EXPECT_TRUE(ws.isWeightedSetTermSearch(adapter, "multi", false));

    EXPECT_EQUAL(expect, ws.search(adapter, "integer", true));
    EXPECT_EQUAL(expect, ws.search(adapter, "integer", false));
    EXPECT_EQUAL(expect, ws.search(adapter, "string", true));
    EXPECT_EQUAL(expect, ws.search(adapter, "string", false));
    EXPECT_EQUAL(expect, ws.search(adapter, "multi", true));
    EXPECT_EQUAL(expect, ws.search(adapter, "multi", false));
}

TEST_MAIN() { TEST_RUN_ALL(); }
