// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributememorysavetarget.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/attrvector.h>
#include <vespa/searchlib/attribute/i_document_weight_attribute.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/searchlib/attribute/multinumericpostattribute.hpp>
#include <vespa/searchlib/attribute/multistringattribute.h>
#include <vespa/searchlib/attribute/multistringpostattribute.hpp>
#include <vespa/searchlib/attribute/singlenumericattribute.h>
#include <vespa/searchlib/attribute/singlestringattribute.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/query/tree/location.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/document_weight_search_iterator.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/util/randomgenerator.h>
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/log/log.h>
LOG_SETUP("document_weight_iterator_test");

using namespace search;
using namespace search::attribute;

AttributeVector::SP make_attribute(BasicType type, CollectionType collection, bool fast_search) {
    Config cfg(type, collection);
    cfg.setFastSearch(fast_search);
    return AttributeFactory::createAttribute("my_attribute", cfg);
}

void add_docs(AttributeVector::SP attr_ptr, size_t limit = 1000) {
    AttributeVector::DocId docid;
    for (size_t i = 0; i < limit; ++i) {
        attr_ptr->addDoc(docid);
    }
    attr_ptr->commit();
    ASSERT_EQUAL((limit - 1), docid);
}

template <typename ATTR, typename KEY>
void set_doc(ATTR *attr, uint32_t docid, KEY key, int32_t weight) {
    attr->clearDoc(docid);
    attr->append(docid, key, weight);
    attr->commit();
}

void populate_long(AttributeVector::SP attr_ptr) {
    IntegerAttribute *attr = static_cast<IntegerAttribute *>(attr_ptr.get());
    set_doc(attr, 1, int64_t(111), 20);
    set_doc(attr, 5, int64_t(111), 5);
    set_doc(attr, 7, int64_t(111), 10);
}

void populate_string(AttributeVector::SP attr_ptr) {
    StringAttribute *attr = static_cast<StringAttribute *>(attr_ptr.get());
    set_doc(attr, 1, "foo", 20);
    set_doc(attr, 5, "foo", 5);
    set_doc(attr, 7, "foo", 10);
}

struct LongFixture {
    AttributeVector::SP attr;
    const IDocumentWeightAttribute *api;
    LongFixture() : attr(make_attribute(BasicType::INT64, CollectionType::WSET, true)),
                    api(attr->asDocumentWeightAttribute())
    {
        ASSERT_TRUE(api != nullptr);
        add_docs(attr);
        populate_long(attr);
    }
};

struct StringFixture {
    AttributeVector::SP attr;
    const IDocumentWeightAttribute *api;
    StringFixture() : attr(make_attribute(BasicType::STRING, CollectionType::WSET, true)),
                      api(attr->asDocumentWeightAttribute())
    {
        ASSERT_TRUE(api != nullptr);
        add_docs(attr);
        populate_string(attr);
    }
};

TEST("require that appropriate attributes support the document weight attribute interface") {
    EXPECT_TRUE(make_attribute(BasicType::INT64,  CollectionType::WSET, true)->asDocumentWeightAttribute() != nullptr);
    EXPECT_TRUE(make_attribute(BasicType::STRING, CollectionType::WSET, true)->asDocumentWeightAttribute() != nullptr);
}

TEST("require that inappropriate attributes do not support the document weight attribute interface") {
    EXPECT_TRUE(make_attribute(BasicType::INT64,  CollectionType::SINGLE, false)->asDocumentWeightAttribute() == nullptr);
    EXPECT_TRUE(make_attribute(BasicType::INT64,  CollectionType::ARRAY,  false)->asDocumentWeightAttribute() == nullptr);
    EXPECT_TRUE(make_attribute(BasicType::INT64,  CollectionType::WSET,   false)->asDocumentWeightAttribute() == nullptr);
    EXPECT_TRUE(make_attribute(BasicType::INT64,  CollectionType::SINGLE,  true)->asDocumentWeightAttribute() == nullptr);
    EXPECT_TRUE(make_attribute(BasicType::INT64,  CollectionType::ARRAY,   true)->asDocumentWeightAttribute() == nullptr);
    EXPECT_TRUE(make_attribute(BasicType::STRING, CollectionType::SINGLE, false)->asDocumentWeightAttribute() == nullptr);
    EXPECT_TRUE(make_attribute(BasicType::STRING, CollectionType::ARRAY,  false)->asDocumentWeightAttribute() == nullptr);
    EXPECT_TRUE(make_attribute(BasicType::STRING, CollectionType::WSET,   false)->asDocumentWeightAttribute() == nullptr);
    EXPECT_TRUE(make_attribute(BasicType::STRING, CollectionType::SINGLE,  true)->asDocumentWeightAttribute() == nullptr);
    EXPECT_TRUE(make_attribute(BasicType::STRING, CollectionType::ARRAY,   true)->asDocumentWeightAttribute() == nullptr);
    EXPECT_TRUE(make_attribute(BasicType::INT32,  CollectionType::WSET,    true)->asDocumentWeightAttribute() == nullptr);
    EXPECT_TRUE(make_attribute(BasicType::DOUBLE, CollectionType::WSET,    true)->asDocumentWeightAttribute() == nullptr);
}

void verify_valid_lookup(IDocumentWeightAttribute::LookupResult result) {
    EXPECT_TRUE(result.posting_idx.valid());
    EXPECT_EQUAL(3u, result.posting_size);
    EXPECT_EQUAL(5, result.min_weight);
    EXPECT_EQUAL(20, result.max_weight);
}

void verify_invalid_lookup(IDocumentWeightAttribute::LookupResult result) {
    EXPECT_FALSE(result.posting_idx.valid());
    EXPECT_EQUAL(0u, result.posting_size);
    EXPECT_EQUAL(0, result.min_weight);
    EXPECT_EQUAL(0, result.max_weight);
}

TEST_F("require that integer lookup works correctly", LongFixture) {
    verify_valid_lookup(f1.api->lookup("111"));
    verify_invalid_lookup(f1.api->lookup("222"));
}

TEST_F("require string lookup works correctly", StringFixture) {
    verify_valid_lookup(f1.api->lookup("foo"));
    verify_invalid_lookup(f1.api->lookup("bar"));
}

void verify_posting(const IDocumentWeightAttribute &api, const char *term) {
    auto result = api.lookup(term);
    ASSERT_TRUE(result.posting_idx.valid());
    std::vector<DocumentWeightIterator> itr_store;
    api.create(result.posting_idx, itr_store);
    ASSERT_EQUAL(1u, itr_store.size());
    {
        DocumentWeightIterator &itr = itr_store[0];
        if (itr.valid() && itr.getKey() < 1) {
            itr.linearSeek(1);
        }
        ASSERT_TRUE(itr.valid());
        EXPECT_EQUAL(1u, itr.getKey());  // docid
        EXPECT_EQUAL(20, itr.getData()); // weight
        itr.linearSeek(2);
        ASSERT_TRUE(itr.valid());
        EXPECT_EQUAL(5u, itr.getKey());  // docid
        EXPECT_EQUAL(5, itr.getData());  // weight
        itr.linearSeek(6);
        ASSERT_TRUE(itr.valid());
        EXPECT_EQUAL(7u, itr.getKey());  // docid
        EXPECT_EQUAL(10, itr.getData()); // weight
        itr.linearSeek(8);
        EXPECT_FALSE(itr.valid());
    }
}

TEST_F("require that integer iterators are created correctly", LongFixture) {
    verify_posting(*f1.api, "111");
}

TEST_F("require that string iterators are created correctly", StringFixture) {
    verify_posting(*f1.api, "foo");
}

class Verifier : public search::test::SearchIteratorVerifier {
public:
    Verifier();
    ~Verifier();
    SearchIterator::UP create(bool strict) const override {
        (void) strict;
        const IDocumentWeightAttribute *api(_attr->asDocumentWeightAttribute());
        ASSERT_TRUE(api != nullptr);
        auto dict_entry = api->lookup("123");
        ASSERT_TRUE(dict_entry.posting_idx.valid());
        return std::make_unique<queryeval::DocumentWeightSearchIterator>(_tfmd, *api, dict_entry);
    }
private:
    mutable fef::TermFieldMatchData _tfmd;
    AttributeVector::SP _attr;
};

Verifier::Verifier()
    : _attr(make_attribute(BasicType::INT64, CollectionType::WSET, true))
{
    add_docs(_attr, getDocIdLimit());
    auto docids = getExpectedDocIds();
    IntegerAttribute *int_attr = static_cast<IntegerAttribute *>(_attr.get());
    for (auto docid: docids) {
        set_doc(int_attr, docid, int64_t(123), 1);
    }
}
Verifier::~Verifier() {}

TEST("verify document weight search iterator") {
    Verifier verifier;
    verifier.verify();
}

TEST_MAIN() { TEST_RUN_ALL(); }
