// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/direct_multi_term_blueprint.h>
#include <vespa/searchlib/attribute/i_docid_posting_store.h>
#include <vespa/searchlib/attribute/i_docid_with_weight_posting_store.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/weighted_set_term_search.h>
#include <vespa/searchlib/test/attribute_builder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>
#include <numeric>

using namespace search::attribute;
using namespace search::queryeval;
using namespace search;
using testing::StartsWith;

using LookupKey = IDirectPostingStore::LookupKey;

struct IntegerKey : public LookupKey {
    int64_t _value;
    IntegerKey(int64_t value_in) : _value(value_in) {}
    vespalib::stringref asString() const override { abort(); }
    bool asInteger(int64_t& value) const override { value = _value; return true; }
};

struct StringKey : public LookupKey {
    vespalib::string _value;
    StringKey(int64_t value_in) : _value(std::to_string(value_in)) {}
    vespalib::stringref asString() const override { return _value; }
    bool asInteger(int64_t&) const override { abort(); }
};

const vespalib::string field_name = "test";
constexpr uint32_t field_id = 3;
uint32_t doc_id_limit = 500;

using Docids = std::vector<uint32_t>;

Docids
range(uint32_t begin, uint32_t count)
{
    Docids res(count);
    std::iota(res.begin(), res.end(), begin);
    return res;
}

Docids
concat(const Docids& a, const Docids& b)
{
    std::vector<uint32_t> res;
    res.insert(res.end(), a.begin(), a.end());
    res.insert(res.end(), b.begin(), b.end());
    std::sort(res.begin(), res.end());
    return res;
}

template <typename AttributeType, typename DataType>
void
populate_attribute(AttributeType& attr, const std::vector<DataType>& values)
{
    // Values 0 and 1 have btree (short) posting lists.
    attr.update(10, values[0]);
    attr.update(30, values[1]);
    attr.update(31, values[1]);

    // Values 2 and 3 have bitvector posting lists.
    // We need at least 128 documents to get bitvector posting list (see PostingStoreBase2::resizeBitVectors())
    for (auto docid : range(100, 128)) {
        attr.update(docid, values[2]);
    }
    for (auto docid : range(300, 128)) {
        attr.update(docid, values[3]);
    }
    attr.commit(true);
}

std::shared_ptr<AttributeVector>
make_attribute(CollectionType col_type, BasicType type, bool field_is_filter)
{
    Config cfg(type, col_type);
    cfg.setFastSearch(true);
    if (field_is_filter) {
        cfg.setIsFilter(field_is_filter);
    }
    uint32_t num_docs = doc_id_limit - 1;
    auto attr = test::AttributeBuilder(field_name, cfg).docs(num_docs).get();
    if (type == BasicType::STRING) {
        populate_attribute<StringAttribute, vespalib::string>(dynamic_cast<StringAttribute&>(*attr),
                                                              {"1", "3", "100", "300"});
    } else {
        populate_attribute<IntegerAttribute, int64_t>(dynamic_cast<IntegerAttribute&>(*attr),
                                                      {1, 3, 100, 300});
    }
    return attr;
}

void
expect_has_btree_iterator(const IDirectPostingStore& store, const LookupKey& key)
{
    auto snapshot = store.get_dictionary_snapshot();
    auto res = store.lookup(key, snapshot);
    EXPECT_TRUE(store.has_btree_iterator(res.posting_idx));
}

void
expect_has_bitvector_iterator(const IDirectPostingStore& store, const LookupKey& key)
{
    auto snapshot = store.get_dictionary_snapshot();
    auto res = store.lookup(key, snapshot);
    EXPECT_TRUE(store.has_bitvector(res.posting_idx));
}

template <typename LookupKeyType>
void
validate_posting_lists(const IDirectPostingStore& store)
{
    expect_has_btree_iterator(store, LookupKeyType(1));
    expect_has_btree_iterator(store, LookupKeyType(3));
    if (store.has_always_btree_iterator()) {
        expect_has_btree_iterator(store, LookupKeyType(100));
        expect_has_btree_iterator(store, LookupKeyType(300));
    }
    expect_has_bitvector_iterator(store, LookupKeyType(100));
    expect_has_bitvector_iterator(store, LookupKeyType(300));
}

struct TestParam {
    CollectionType col_type;
    BasicType type;
    TestParam(CollectionType col_type_in, BasicType type_in) : col_type(col_type_in), type(type_in) {}
    ~TestParam() = default;
};

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << param.col_type.asString() << "_" << param.type.asString();
    return os;
}

class DirectMultiTermBlueprintTest : public ::testing::TestWithParam<TestParam> {
public:
    using SingleValueBlueprintType = DirectMultiTermBlueprint<IDocidPostingStore, WeightedSetTermSearch>;
    using MultiValueBlueprintType = DirectMultiTermBlueprint<IDocidWithWeightPostingStore, WeightedSetTermSearch>;
    std::shared_ptr<AttributeVector> attr;
    bool integer_type;
    std::shared_ptr<SingleValueBlueprintType> single_blueprint;
    std::shared_ptr<MultiValueBlueprintType> multi_blueprint;
    queryeval::ComplexLeafBlueprint* blueprint;
    Blueprint::HitEstimate estimate;
    fef::TermFieldMatchData tfmd;
    fef::TermFieldMatchDataArray tfmda;
    DirectMultiTermBlueprintTest()
        : attr(),
          integer_type(true),
          single_blueprint(),
          multi_blueprint(),
          blueprint(),
          tfmd(),
          tfmda()
    {
        tfmda.add(&tfmd);
    }
    void setup(bool field_is_filter, bool need_term_field_match_data) {
        attr = make_attribute(GetParam().col_type, GetParam().type, field_is_filter);
        integer_type = GetParam().type != BasicType::STRING;
        FieldSpec spec(field_name, field_id, fef::TermFieldHandle(), field_is_filter);
        const IDirectPostingStore* store;
        if (GetParam().col_type == CollectionType::SINGLE) {
            auto real_store = attr->as_docid_posting_store();
            ASSERT_TRUE(real_store);
            single_blueprint = std::make_shared<SingleValueBlueprintType>(spec, *attr, *real_store, 2);
            blueprint = single_blueprint.get();
            store = real_store;
        } else {
            auto real_store = attr->as_docid_with_weight_posting_store();
            ASSERT_TRUE(real_store);
            multi_blueprint = std::make_shared<MultiValueBlueprintType>(spec, *attr, *real_store, 2);
            blueprint = multi_blueprint.get();
            store = real_store;
        }
        if (integer_type) {
            validate_posting_lists<IntegerKey>(*store);
        } else {
            validate_posting_lists<StringKey>(*store);
        }
        blueprint->setDocIdLimit(doc_id_limit);
        if (need_term_field_match_data) {
            tfmd.needs_normal_features();
        } else {
            tfmd.tagAsNotNeeded();
        }
    }
    template <typename BlueprintType>
    void add_term_helper(BlueprintType& b, int64_t term_value) {
        if (integer_type) {
            b.addTerm(IntegerKey(term_value), 1, estimate);
        } else {
            b.addTerm(StringKey(term_value), 1, estimate);
        }
    }
    void add_term(int64_t term_value) {
        if (single_blueprint) {
            add_term_helper(*single_blueprint, term_value);
        } else {
            add_term_helper(*multi_blueprint, term_value);
        }
    }
    std::unique_ptr<SearchIterator> create_leaf_search() const {
        return blueprint->createLeafSearch(tfmda, true);
    }
};

void
expect_hits(const Docids& exp_docids, SearchIterator& itr)
{
    SimpleResult exp(exp_docids);
    SimpleResult act;
    act.search(itr);
    EXPECT_EQ(exp, act);
}

void
expect_or_iterator(SearchIterator& itr, size_t exp_children)
{
    auto& real = dynamic_cast<OrSearch&>(itr);
    ASSERT_EQ(exp_children, real.getChildren().size());
}

void
expect_or_child(SearchIterator& itr, size_t child, const vespalib::string& exp_child_itr)
{
    auto& real = dynamic_cast<OrSearch&>(itr);
    EXPECT_THAT(real.getChildren()[child]->asString(), StartsWith(exp_child_itr));
}

INSTANTIATE_TEST_SUITE_P(DefaultInstantiation,
                         DirectMultiTermBlueprintTest,
                         testing::Values(TestParam(CollectionType::SINGLE, BasicType::INT64),
                                         TestParam(CollectionType::SINGLE, BasicType::STRING),
                                         TestParam(CollectionType::WSET, BasicType::INT64),
                                         TestParam(CollectionType::WSET, BasicType::STRING)),
                         testing::PrintToStringParamName());

TEST_P(DirectMultiTermBlueprintTest, weight_iterators_used_for_none_filter_field)
{
    setup(false, true);
    add_term(1);
    add_term(3);
    auto itr = create_leaf_search();
    EXPECT_THAT(itr->asString(), StartsWith("search::queryeval::WeightedSetTermSearchImpl"));
    expect_hits({10, 30, 31}, *itr);
}

TEST_P(DirectMultiTermBlueprintTest, weight_iterators_used_instead_of_bitvectors_for_none_filter_field)
{
    setup(false, true);
    add_term(1);
    add_term(100);
    auto itr = create_leaf_search();
    EXPECT_THAT(itr->asString(), StartsWith("search::queryeval::WeightedSetTermSearchImpl"));
    expect_hits(concat({10}, range(100, 128)), *itr);
}

TEST_P(DirectMultiTermBlueprintTest, bitvectors_and_weight_iterators_used_for_filter_field)
{
    setup(true, true);
    add_term(1);
    add_term(3);
    add_term(100);
    add_term(300);
    auto itr = create_leaf_search();
    expect_or_iterator(*itr, 3);
    expect_or_child(*itr, 0, "search::BitVectorIteratorStrictT");
    expect_or_child(*itr, 1, "search::BitVectorIteratorStrictT");
    expect_or_child(*itr, 2, "search::queryeval::WeightedSetTermSearchImpl");
    expect_hits(concat({10, 30, 31}, concat(range(100, 128), range(300, 128))), *itr);
}

TEST_P(DirectMultiTermBlueprintTest, only_bitvectors_used_for_filter_field)
{
    setup(true, true);
    add_term(100);
    add_term(300);
    auto itr = create_leaf_search();
    expect_or_iterator(*itr, 2);
    expect_or_child(*itr, 0, "search::BitVectorIteratorStrictT");
    expect_or_child(*itr, 1, "search::BitVectorIteratorStrictT");
    expect_hits(concat(range(100, 128), range(300, 128)), *itr);
}

TEST_P(DirectMultiTermBlueprintTest, filter_iterator_used_for_filter_field_and_ranking_not_needed)
{
    setup(true, false);
    add_term(1);
    add_term(3);
    auto itr = create_leaf_search();
    EXPECT_THAT(itr->asString(), StartsWith("search::attribute::DocumentWeightOrFilterSearchImpl"));
    expect_hits({10, 30, 31}, *itr);
}

TEST_P(DirectMultiTermBlueprintTest, bitvectors_and_filter_iterator_used_for_filter_field_and_ranking_not_needed)
{
    setup(true, false);
    add_term(1);
    add_term(3);
    add_term(100);
    add_term(300);
    auto itr = create_leaf_search();
    expect_or_iterator(*itr, 3);
    expect_or_child(*itr, 0, "search::BitVectorIteratorStrictT");
    expect_or_child(*itr, 1, "search::BitVectorIteratorStrictT");
    expect_or_child(*itr, 2, "search::attribute::DocumentWeightOrFilterSearchImpl");
    expect_hits(concat({10, 30, 31}, concat(range(100, 128), range(300, 128))), *itr);
}

TEST_P(DirectMultiTermBlueprintTest, only_bitvectors_used_for_filter_field_and_ranking_not_needed)
{
    setup(true, false);
    add_term(100);
    add_term(300);
    auto itr = create_leaf_search();
    expect_or_iterator(*itr, 2);
    expect_or_child(*itr, 0, "search::BitVectorIteratorStrictT");
    expect_or_child(*itr, 1, "search::BitVectorIteratorStrictT");
    expect_hits(concat(range(100, 128), range(300, 128)), *itr);
}

GTEST_MAIN_RUN_ALL_TESTS()
