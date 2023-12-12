// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/direct_multi_term_blueprint.h>
#include <vespa/searchlib/attribute/integerbase.h>
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

struct IntegerKey : public IDirectPostingStore::LookupKey {
    int64_t _value;
    IntegerKey(int64_t value_in) : _value(value_in) {}
    vespalib::stringref asString() const override { abort(); }
    bool asInteger(int64_t& value) const override { value = _value; return true; }
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

std::shared_ptr<AttributeVector>
make_attribute(bool field_is_filter, CollectionType col_type)
{
    Config cfg(BasicType::INT64, col_type);
    cfg.setFastSearch(true);
    if (field_is_filter) {
        cfg.setIsFilter(field_is_filter);
    }
    uint32_t num_docs = doc_id_limit - 1;
    auto attr = test::AttributeBuilder(field_name, cfg).docs(num_docs).get();
    IntegerAttribute& real = dynamic_cast<IntegerAttribute&>(*attr);

    // Values 1 and 3 have btree (short) posting lists with weights.
    real.append(10, 1, 1);
    real.append(30, 3, 1);
    real.append(31, 3, 1);

    // Values 100 and 300 have bitvector posting lists.
    // We need at least 128 documents to get bitvector posting list (see PostingStoreBase2::resizeBitVectors())
    for (auto docid : range(100, 128)) {
        real.append(docid, 100, 1);
    }
    for (auto docid : range(300, 128)) {
        real.append(docid, 300, 1);
    }
    attr->commit(true);
    return attr;
}

void
expect_has_weight_iterator(const IDirectPostingStore& store, int64_t term_value)
{
    auto snapshot = store.get_dictionary_snapshot();
    auto res = store.lookup(IntegerKey(term_value), snapshot);
    EXPECT_TRUE(store.has_weight_iterator(res.posting_idx));
}

void
expect_has_bitvector_iterator(const IDirectPostingStore& store, int64_t term_value)
{
    auto snapshot = store.get_dictionary_snapshot();
    auto res = store.lookup(IntegerKey(term_value), snapshot);
    EXPECT_TRUE(store.has_bitvector(res.posting_idx));
}

void
validate_posting_lists(const IDocidWithWeightPostingStore& store)
{
    expect_has_weight_iterator(store, 1);
    expect_has_weight_iterator(store, 3);
    if (store.has_always_weight_iterator()) {
        expect_has_weight_iterator(store, 100);
        expect_has_weight_iterator(store, 300);
    }
    expect_has_bitvector_iterator(store, 100);
    expect_has_bitvector_iterator(store, 300);
}

struct TestParam {
    CollectionType col_type;
    TestParam(CollectionType col_type_in) : col_type(col_type_in) {}
    ~TestParam() = default;
};

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << param.col_type.asString();
    return os;
}

class DirectMultiTermBlueprintTest : public ::testing::TestWithParam<TestParam> {
public:
    using BlueprintType = DirectMultiTermBlueprint<IDocidWithWeightPostingStore, WeightedSetTermSearch>;
    std::shared_ptr<AttributeVector> attr;
    std::shared_ptr<BlueprintType> blueprint;
    Blueprint::HitEstimate estimate;
    fef::TermFieldMatchData tfmd;
    fef::TermFieldMatchDataArray tfmda;
    DirectMultiTermBlueprintTest()
        : attr(),
          blueprint(),
          tfmd(),
          tfmda()
    {
        tfmda.add(&tfmd);
    }
    void setup(bool field_is_filter, bool need_term_field_match_data) {
        attr = make_attribute(field_is_filter, GetParam().col_type);
        const auto* store = attr->as_docid_with_weight_posting_store();
        ASSERT_TRUE(store);
        validate_posting_lists(*store);
        blueprint = std::make_shared<BlueprintType>(FieldSpec(field_name, field_id, fef::TermFieldHandle(), field_is_filter), *attr, *store, 2);
        blueprint->setDocIdLimit(doc_id_limit);
        if (need_term_field_match_data) {
            tfmd.needs_normal_features();
        } else {
            tfmd.tagAsNotNeeded();
        }
    }
    void add_term(int64_t term_value) {
        blueprint->addTerm(IntegerKey(term_value), 1, estimate);
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
                         testing::Values(CollectionType::WSET),
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
