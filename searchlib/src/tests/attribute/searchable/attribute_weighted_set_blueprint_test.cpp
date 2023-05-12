// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
setupAttributeManager(MockAttributeManager &manager)
{
    AttributeVector::DocId docId;
    {
        AttributeVector::SP attr_sp = AttributeFactory::createAttribute("integer", Config(BasicType("int64")));
        manager.addAttribute(attr_sp);

        IntegerAttribute *attr = (IntegerAttribute*)(attr_sp.get());
        for (size_t i = 1; i < 10; ++i) {
            attr->addDoc(docId);
            assert(i == docId);
            attr->update(docId, i);
            attr->commit();
        }
    }
    {
        AttributeVector::SP attr_sp = AttributeFactory::createAttribute("string", Config(BasicType("string")));
        manager.addAttribute(attr_sp);

        StringAttribute *attr = (StringAttribute*)(attr_sp.get());
        for (size_t i = 1; i < 10; ++i) {
            attr->addDoc(docId);
            assert(i == docId);
            attr->update(i, std::string(1, '1' + i - 1).c_str());
            attr->commit();
        }
    }
    {
        AttributeVector::SP attr_sp = AttributeFactory::createAttribute(
                "multi", Config(BasicType("int64"), search::attribute::CollectionType("array")));
        manager.addAttribute(attr_sp);
        IntegerAttribute *attr = (IntegerAttribute*)(attr_sp.get());
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

    WS(IAttributeManager & manager) : attribute_manager(manager), layout(), handle(layout.allocTermField(fieldId)), tokens() {
        MatchData::UP tmp = layout.createMatchData();
        ASSERT_TRUE(tmp->resolveTermField(handle)->getFieldId() == fieldId);
    }

    WS &add(const std::string &token, uint32_t weight) {
        tokens.push_back(std::make_pair(token, weight));
        return *this;
    }

    Node::UP createNode() const {
        SimpleWeightedSetTerm *node = new SimpleWeightedSetTerm(tokens.size(), "view", 0, Weight(0));
        for (size_t i = 0; i < tokens.size(); ++i) {
            node->addTerm(tokens[i].first, Weight(tokens[i].second));
        }
        return Node::UP(node);
    }

    bool isGenericSearch(Searchable &searchable, const std::string &field, bool strict) const {
        AttributeContext ac(attribute_manager);
        FakeRequestContext requestContext(&ac);
        MatchData::UP md = layout.createMatchData();
        Node::UP node = createNode();
        FieldSpecList fields;
        fields.add(FieldSpec(field, fieldId, handle));
        queryeval::Blueprint::UP bp = searchable.createBlueprint(requestContext, fields, *node);
        bp->fetchPostings(queryeval::ExecuteInfo::create(strict));
        SearchIterator::UP sb = bp->createSearch(*md, strict);
        return (dynamic_cast<WeightedSetTermSearch*>(sb.get()) != 0);
    }

    FakeResult search(Searchable &searchable, const std::string &field, bool strict) const {
        AttributeContext ac(attribute_manager);
        FakeRequestContext requestContext(&ac);
        MatchData::UP md = layout.createMatchData();
        Node::UP node = createNode();
        FieldSpecList fields;
        fields.add(FieldSpec(field, fieldId, handle));
        queryeval::Blueprint::UP bp = searchable.createBlueprint(requestContext, fields, *node);
        bp->fetchPostings(queryeval::ExecuteInfo::create(strict));
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

TEST("attribute_weighted_set_test") {
    MockAttributeManager manager;
    setupAttributeManager(manager);
    AttributeBlueprintFactory adapter;

    FakeResult expect = FakeResult()
                        .doc(3).elem(0).weight(30).pos(0)
                        .doc(5).elem(0).weight(50).pos(0)
                        .doc(7).elem(0).weight(70).pos(0);
    WS ws = WS(manager).add("7", 70).add("5", 50).add("3", 30);

    EXPECT_TRUE(ws.isGenericSearch(adapter, "integer", true));
    EXPECT_TRUE(!ws.isGenericSearch(adapter, "integer", false));
    EXPECT_TRUE(ws.isGenericSearch(adapter, "string", true));
    EXPECT_TRUE(!ws.isGenericSearch(adapter, "string", false));
    EXPECT_TRUE(ws.isGenericSearch(adapter, "multi", true));
    EXPECT_TRUE(ws.isGenericSearch(adapter, "multi", false));

    EXPECT_EQUAL(expect, ws.search(adapter, "integer", true));
    EXPECT_EQUAL(expect, ws.search(adapter, "integer", false));
    EXPECT_EQUAL(expect, ws.search(adapter, "string", true));
    EXPECT_EQUAL(expect, ws.search(adapter, "string", false));
    EXPECT_EQUAL(expect, ws.search(adapter, "multi", true));
    EXPECT_EQUAL(expect, ws.search(adapter, "multi", false));
}

TEST_MAIN() { TEST_RUN_ALL(); }
