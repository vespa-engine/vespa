// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributememorysavetarget.h>
#include <vespa/searchlib/attribute/i_docid_posting_store.h>
#include <vespa/searchlib/attribute/i_docid_with_weight_posting_store.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/queryeval/docid_with_weight_search_iterator.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/util/randomgenerator.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("direct_posting_store_test");

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
    ASSERT_EQ((limit - 1), docid);
}

template <typename ATTR, typename KEY>
void set_doc(ATTR *attr, uint32_t docid, KEY key, int32_t weight) {
    attr->clearDoc(docid);
    if (attr->getCollectionType() == CollectionType::SINGLE) {
        attr->update(docid, key);
    } else {
        attr->append(docid, key, weight);
    }
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

struct TestParam {
    CollectionType col_type;
    BasicType type;
    const char* valid_term;
    const char* invalid_term;
    TestParam(CollectionType col_type_in, BasicType type_in,
              const char* valid_term_in, const char* invalid_term_in)
        : col_type(col_type_in), type(type_in), valid_term(valid_term_in), invalid_term(invalid_term_in) {}
    ~TestParam() {}
};

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << param.col_type.asString() << "_" << param.type.asString();
    return os;
}

struct DirectPostingStoreTest : public ::testing::TestWithParam<TestParam> {
    AttributeVector::SP attr;
    bool has_weight;
    const IDirectPostingStore* api;

    const IDirectPostingStore* extract_api() {
        if (has_weight) {
            return attr->as_docid_with_weight_posting_store();
        } else {
            return attr->as_docid_posting_store();
        }
    }

    DirectPostingStoreTest()
        : attr(make_attribute(GetParam().type, GetParam().col_type, true)),
          has_weight(GetParam().col_type != CollectionType::SINGLE),
          api(extract_api())
    {
        assert(api != nullptr);
        add_docs(attr);
        if (GetParam().type == BasicType::STRING) {
            populate_string(attr);
        } else {
            populate_long(attr);
        }
    }
    ~DirectPostingStoreTest() {}
};

void expect_docid_posting_store(BasicType type, CollectionType col_type, bool fast_search) {
    EXPECT_TRUE(make_attribute(type, col_type, fast_search)->as_docid_posting_store() != nullptr);
}

void expect_not_docid_posting_store(BasicType type, CollectionType col_type, bool fast_search) {
    EXPECT_TRUE(make_attribute(type, col_type, fast_search)->as_docid_posting_store() == nullptr);
}

void expect_docid_with_weight_posting_store(BasicType type, CollectionType col_type, bool fast_search) {
    EXPECT_TRUE(make_attribute(type, col_type, fast_search)->as_docid_with_weight_posting_store() != nullptr);
}

void expect_not_docid_with_weight_posting_store(BasicType type, CollectionType col_type, bool fast_search) {
    EXPECT_TRUE(make_attribute(type, col_type, fast_search)->as_docid_with_weight_posting_store() == nullptr);
}

TEST(DirectPostingStoreApiTest, attributes_support_IDocidPostingStore_interface) {
    expect_docid_posting_store(BasicType::INT8, CollectionType::SINGLE, true);
    expect_docid_posting_store(BasicType::INT16, CollectionType::SINGLE, true);
    expect_docid_posting_store(BasicType::INT32, CollectionType::SINGLE, true);
    expect_docid_posting_store(BasicType::INT64, CollectionType::SINGLE, true);
    expect_docid_posting_store(BasicType::STRING, CollectionType::SINGLE, true);
}

TEST(DirectPostingStoreApiTest, attributes_do_not_support_IDocidPostingStore_interface) {
    expect_not_docid_posting_store(BasicType::BOOL, CollectionType::SINGLE, true);
    expect_not_docid_posting_store(BasicType::FLOAT, CollectionType::SINGLE, true);
    expect_not_docid_posting_store(BasicType::DOUBLE, CollectionType::SINGLE, true);
    expect_not_docid_posting_store(BasicType::INT64, CollectionType::SINGLE, false);
    expect_not_docid_posting_store(BasicType::STRING, CollectionType::SINGLE, false);
}

TEST(DirectPostingStoreApiTest, attributes_support_IDocidWithWeightPostingStore_interface) {
    expect_docid_with_weight_posting_store(BasicType::INT64, CollectionType::WSET, true);
    expect_docid_with_weight_posting_store(BasicType::INT32, CollectionType::WSET, true);
    expect_docid_with_weight_posting_store(BasicType::STRING, CollectionType::WSET, true);
    expect_docid_with_weight_posting_store(BasicType::INT64, CollectionType::ARRAY, true);
    expect_docid_with_weight_posting_store(BasicType::INT32, CollectionType::ARRAY, true);
    expect_docid_with_weight_posting_store(BasicType::STRING, CollectionType::ARRAY, true);
}

TEST(DirectPostingStoreApiTest, attributes_do_not_support_IDocidWithWeightPostingStore_interface) {
    expect_not_docid_with_weight_posting_store(BasicType::INT64, CollectionType::SINGLE, false);
    expect_not_docid_with_weight_posting_store(BasicType::INT64, CollectionType::ARRAY, false);
    expect_not_docid_with_weight_posting_store(BasicType::INT64, CollectionType::WSET, false);
    expect_not_docid_with_weight_posting_store(BasicType::INT64, CollectionType::SINGLE, true);
    expect_not_docid_with_weight_posting_store(BasicType::STRING, CollectionType::SINGLE, false);
    expect_not_docid_with_weight_posting_store(BasicType::STRING, CollectionType::ARRAY, false);
    expect_not_docid_with_weight_posting_store(BasicType::STRING, CollectionType::WSET, false);
    expect_not_docid_with_weight_posting_store(BasicType::STRING, CollectionType::SINGLE, true);
    expect_not_docid_with_weight_posting_store(BasicType::DOUBLE, CollectionType::ARRAY, true);
    expect_not_docid_with_weight_posting_store(BasicType::DOUBLE, CollectionType::WSET, true);
}

void verify_valid_lookup(IDirectPostingStore::LookupResult result, bool has_weight) {
    EXPECT_TRUE(result.posting_idx.valid());
    EXPECT_EQ(3u, result.posting_size);
    EXPECT_EQ(has_weight ? 5 : 1, result.min_weight);
    EXPECT_EQ(has_weight ? 20 : 1, result.max_weight);
}

void verify_invalid_lookup(IDirectPostingStore::LookupResult result) {
    EXPECT_FALSE(result.posting_idx.valid());
    EXPECT_EQ(0u, result.posting_size);
    EXPECT_EQ(0, result.min_weight);
    EXPECT_EQ(0, result.max_weight);
}

INSTANTIATE_TEST_SUITE_P(DefaultInstantiation,
                         DirectPostingStoreTest,
                         testing::Values(TestParam(CollectionType::SINGLE, BasicType::INT64, "111", "222"),
                                 TestParam(CollectionType::WSET, BasicType::INT64, "111", "222"),
                                 TestParam(CollectionType::SINGLE, BasicType::STRING, "foo", "bar"),
                                 TestParam(CollectionType::WSET, BasicType::STRING, "foo", "bar")),
                         testing::PrintToStringParamName());

TEST_P(DirectPostingStoreTest, lookup_works_correctly) {
    verify_valid_lookup(api->lookup(GetParam().valid_term, api->get_dictionary_snapshot()), has_weight);
    verify_invalid_lookup(api->lookup(GetParam().invalid_term, api->get_dictionary_snapshot()));
}

template <typename DirectPostingStoreType, bool has_weight>
void verify_posting(const IDirectPostingStore& api, const std::string& term) {
    auto result = api.lookup(term, api.get_dictionary_snapshot());
    ASSERT_TRUE(result.posting_idx.valid());
    std::vector<typename DirectPostingStoreType::IteratorType> itr_store;
    auto& real = dynamic_cast<const DirectPostingStoreType&>(api);
    real.create(result.posting_idx, itr_store);
    ASSERT_EQ(1u, itr_store.size());
    {
        auto& itr = itr_store[0];
        if (itr.valid() && itr.getKey() < 1) {
            itr.linearSeek(1);
        }
        ASSERT_TRUE(itr.valid());
        EXPECT_EQ(1u, itr.getKey());  // docid
        if constexpr (has_weight) {
            EXPECT_EQ(20, itr.getData()); // weight
        }
        itr.linearSeek(2);
        ASSERT_TRUE(itr.valid());
        EXPECT_EQ(5u, itr.getKey());  // docid
        if constexpr (has_weight) {
            EXPECT_EQ(5, itr.getData());  // weight
        }
        itr.linearSeek(6);
        ASSERT_TRUE(itr.valid());
        EXPECT_EQ(7u, itr.getKey());  // docid
        if constexpr (has_weight) {
            EXPECT_EQ(10, itr.getData()); // weight
        }
        itr.linearSeek(8);
        EXPECT_FALSE(itr.valid());
    }
}

TEST_P(DirectPostingStoreTest, iterators_are_created_correctly) {
    if (has_weight) {
        verify_posting<IDocidWithWeightPostingStore, true>(*api, GetParam().valid_term);
    } else {
        verify_posting<IDocidPostingStore, false>(*api, GetParam().valid_term);
    }
}

TEST_P(DirectPostingStoreTest, collect_folded_works)
{
    if (GetParam().type == BasicType::STRING) {
        auto* sa = static_cast<StringAttribute*>(attr.get());
        set_doc(sa, 2, "bar", 30);
        attr->commit();
        set_doc(sa, 3, "FOO", 30);
        attr->commit();
        auto snapshot = api->get_dictionary_snapshot();
        auto lookup = api->lookup(GetParam().valid_term, snapshot);
        std::vector<std::string> folded;
        std::function<void(vespalib::datastore::EntryRef)> save_folded = [&folded,sa](vespalib::datastore::EntryRef enum_idx) { folded.emplace_back(sa->getFromEnum(enum_idx.ref())); };
        api->collect_folded(lookup.enum_idx, snapshot, save_folded);
        std::vector<std::string> expected_folded{"FOO", "foo"};
        EXPECT_EQ(expected_folded, folded);
    } else {
        auto* ia = dynamic_cast<IntegerAttributeTemplate<int64_t>*>(attr.get());
        set_doc(ia, 2, int64_t(112), 30);
        attr->commit();
        auto snapshot = api->get_dictionary_snapshot();
        auto lookup = api->lookup(GetParam().valid_term, snapshot);
        std::vector<int64_t> folded;
        std::function<void(vespalib::datastore::EntryRef)> save_folded = [&folded, ia](
                vespalib::datastore::EntryRef enum_idx) { folded.emplace_back(ia->getFromEnum(enum_idx.ref())); };
        api->collect_folded(lookup.enum_idx, snapshot, save_folded);
        std::vector<int64_t> expected_folded{int64_t(111)};
        EXPECT_EQ(expected_folded, folded);
    }
}

class Verifier : public search::test::SearchIteratorVerifier {
public:
    Verifier();
    ~Verifier();
    SearchIterator::UP create(bool strict) const override {
        (void) strict;
        const auto* api = _attr->as_docid_with_weight_posting_store();
        assert(api != nullptr);
        auto dict_entry = api->lookup("123", api->get_dictionary_snapshot());
        assert(dict_entry.posting_idx.valid());
        return std::make_unique<queryeval::DocidWithWeightSearchIterator>(_tfmd, *api, dict_entry);
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
    auto* int_attr = static_cast<IntegerAttribute*>(_attr.get());
    for (auto docid : docids) {
        set_doc(int_attr, docid, int64_t(123), 1);
    }
}
Verifier::~Verifier() {}

TEST(VerifierTest, verify_document_weight_search_iterator) {
    Verifier verifier;
    verifier.verify();
}

GTEST_MAIN_RUN_ALL_TESTS()
