// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchlib/attribute/imported_search_context.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/test/imported_attribute_fixture.h>
#include <vespa/searchlib/test/mock_gid_to_lid_mapping.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace search::attribute {

using fef::TermFieldMatchData;
using queryeval::SearchIterator;
using queryeval::SimpleResult;
using vespalib::Trinary;

struct Fixture : ImportedAttributeFixture {

    Fixture(bool useSearchCache = false, FastSearchConfig fastSearch = FastSearchConfig::Default)
        : ImportedAttributeFixture(useSearchCache, fastSearch)
    {}
    ~Fixture() override;

    std::unique_ptr<ImportedSearchContext>
    create_context(std::unique_ptr<QueryTermSimple> term) {
        return std::make_unique<ImportedSearchContext>(std::move(term), SearchContextParams(), *imported_attr, *target_attr);
    }

    std::unique_ptr<SearchIterator>
    create_iterator(ImportedSearchContext& ctx,TermFieldMatchData& match,bool strict) {
        auto iter = ctx.createIterator(&match, strict);
        assert(iter.get() != nullptr);
        iter->initRange(DocId(1), reference_attr->getNumDocs());
        return iter;
    }

    std::unique_ptr<SearchIterator>
    create_non_strict_iterator(ImportedSearchContext& ctx, TermFieldMatchData& match) {
        return create_iterator(ctx, match, false);
    }

    std::unique_ptr<SearchIterator>
    create_strict_iterator(ImportedSearchContext& ctx,TermFieldMatchData& match) {
        return create_iterator(ctx, match, true);
    }

    SimpleResult search(SearchIterator& iter) {
        return SimpleResult().search(iter, get_imported_attr()->getNumDocs());
    }
};

Fixture::~Fixture() = default;

template <typename Iterator>
bool is_hit_with_weight(Iterator& iter, TermFieldMatchData& match, DocId lid, int32_t weight) {
    bool failed = false;
    EXPECT_TRUE(iter.seek(lid)) << (failed = true, "");
    if (failed) {
        return false;
    }
    iter.unpack(lid);
    EXPECT_TRUE(match.has_ranking_data(lid)) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(weight, match.getWeight()) << (failed = true, "");
    return !failed;
}

template <typename Iterator>
bool is_strict_hit_with_weight(Iterator& iter, TermFieldMatchData& match,
                               DocId seek_lid, DocId expected_lid, int32_t weight) {
    bool failed = false;
    iter.seek(seek_lid);
    EXPECT_EQ(expected_lid, iter.getDocId()) << (failed = true, "");
    if (failed) {
        return false;
    }
    iter.unpack(expected_lid);
    EXPECT_TRUE(match.has_ranking_data(expected_lid))  << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(weight, match.getWeight()) << (failed = true, "");
    return !failed;
}

TEST(ImportedSearchContextTest, calc_hit_estimate_returns_document_count_of_reference_attribute_when_not_using_fast_search_target_attribute)
{
    Fixture f;
    add_n_docs_with_undefined_values(*f.target_attr, 10);
    add_n_docs_with_undefined_values(*f.reference_attr, 101);

    auto ctx = f.create_context(word_term("foo"));
    auto est = ctx->calc_hit_estimate();
    EXPECT_EQ(101u, est.est_hits());
    EXPECT_TRUE(est.is_unknown());
}

TEST(ImportedSearchContextTest, calc_hit_estimate_estimates_hits_when_using_fast_search_target_attribute)
{
    Fixture f(false, FastSearchConfig::ExplicitlyEnabled);
    constexpr uint32_t target_docs = 1000;
    constexpr uint32_t docs = 10000;
    constexpr uint32_t target_gids = 200;
    f.target_attr->addReservedDoc();
    add_n_docs_with_undefined_values(*f.target_attr, target_docs);
    f.reference_attr->addReservedDoc();
    add_n_docs_with_undefined_values(*f.reference_attr, docs);
    auto target_attr = dynamic_cast<IntegerAttribute *>(f.target_attr.get());
    // 2 documents with value 20, 110 documents with value 30.
    for (uint32_t i = 1; i < 3; ++i) {
        target_attr->update(i, 20);
    }
    for (uint32_t i = 10; i < 120; ++i) {
        target_attr->update(i, 30);
    }
    f.target_attr->commit();
    // Assign target gids
    for (uint32_t i = 1; i <= target_gids; ++i) {
        auto target_gid = dummy_gid(i);
        f.mapper_factory->_map[target_gid] = i;
        f.reference_attr->notifyReferencedPut(target_gid, i);
    }
    // Add 2 references to each target gid
    for (uint32_t i = 1; i <= target_gids * 2; ++i) {
        f.reference_attr->update(i, dummy_gid(((i - 1) % target_gids) + 1));
    }
    f.reference_attr->commit();
    auto ctx = f.create_context(word_term("10"));
    // Exact count: 0 target hits => 0
    auto est = ctx->calc_hit_estimate();
    EXPECT_EQ(0u, est.est_hits());
    EXPECT_FALSE(est.is_unknown());
    TermFieldMatchData match;
    auto iter = f.create_iterator(*ctx, match, false);
    EXPECT_TRUE(iter->matches_any() == Trinary::False);
    ctx = f.create_context(word_term("20"));
    // Exact count: 2 target hits, 2 docs / target doc => 2 * 2 = 4
    est = ctx->calc_hit_estimate();
    EXPECT_EQ(4u, est.est_hits());
    EXPECT_FALSE(est.is_unknown());
    ctx = f.create_context(word_term("30"));
    // Approximation: 110 target hits => 110 * 10001 / 1001 = 1099
    est = ctx->calc_hit_estimate();
    EXPECT_EQ(1099u, est.est_hits());
    EXPECT_FALSE(est.is_unknown());
}

TEST(ImportedSearchContextTest, attributeName_returns_imported_attribute_name)
{
    Fixture f;
    auto ctx = f.create_context(word_term("foo"));
    EXPECT_EQ(f.default_imported_attr_name(), ctx->attributeName());
}

TEST(ImportedSearchContextTest, valid_forwards_to_target_search_context)
{
    Fixture f;
    auto ctx = f.create_context(word_term("foo"));
    EXPECT_EQ(ctx->target_search_context().valid(), ctx->valid());
}

TEST(ImportedSearchContextTest, getAsIntegerTerm_forwards_to_target_search_context)
{
    Fixture f;
    auto ctx = f.create_context(word_term("foo"));
    // No operator== or printing for Range, so doing this the hard way
    auto expected_range = ctx->target_search_context().getAsIntegerTerm();
    auto actual_range = ctx->getAsIntegerTerm();
    EXPECT_EQ(expected_range.lower(), actual_range.lower());
    EXPECT_EQ(expected_range.upper(), actual_range.upper());
}

TEST(ImportedSearchContextTest, non_strict_iterator_not_marked_as_strict)
{
    Fixture f;
    auto ctx = f.create_context(word_term("5678"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    EXPECT_TRUE(iter->is_strict() == Trinary::False); // No EXPECT_EQUALS printing of Trinary...
}

TEST(ImportedSearchContextTest, non_strict_iterator_seek_forwards_to_target_attribute)
{
    Fixture f;
    reset_with_single_value_reference_mappings<IntegerAttribute, int32_t>(
            f, BasicType::INT32,
            {{DocId(1), dummy_gid(3), DocId(3), 1234},
             {DocId(3), dummy_gid(7), DocId(7), 5678},
             {DocId(5), dummy_gid(8), DocId(8), 7890}});

    auto ctx = f.create_context(word_term("5678"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    EXPECT_FALSE(iter->isAtEnd());
    EXPECT_EQ(iter->beginId(), iter->getDocId());

    EXPECT_FALSE(iter->seek(DocId(1)));
    EXPECT_EQ(iter->beginId(), iter->getDocId()); // Non-strict iterator does not change current ID

    EXPECT_TRUE(iter->seek(DocId(3)));
    EXPECT_EQ(DocId(3), iter->getDocId());

    EXPECT_FALSE(iter->seek(DocId(5)));
    EXPECT_EQ(DocId(3), iter->getDocId()); // Still unchanged
}

TEST(ImportedSearchContextTest, non_strict_iterator_unpacks_target_match_data_for_single_value_hit)
{
    Fixture f;
    reset_with_single_value_reference_mappings<IntegerAttribute, int32_t>(
            f, BasicType::INT32,
            {{DocId(1), dummy_gid(3), DocId(3), 1234},
             {DocId(2), dummy_gid(4), DocId(4), 1234}});

    auto ctx = f.create_context(word_term("1234"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_hit_with_weight(*iter, match, DocId(1), 1));
    EXPECT_TRUE(is_hit_with_weight(*iter, match, DocId(2), 1));
}

struct ArrayValueFixture : Fixture {
    ArrayValueFixture() {
        const std::vector<int64_t> doc3_values({1234});
        const std::vector<int64_t> doc7_values({1234, 1234, 1234, 777});
        const std::vector<int64_t> doc8_values({});
        reset_with_array_value_reference_mappings<IntegerAttribute, int64_t>(
                BasicType::INT64,
                {{DocId(1), dummy_gid(3), DocId(3), doc3_values},
                 {DocId(4), dummy_gid(7), DocId(7), doc7_values},
                 {DocId(5), dummy_gid(8), DocId(8), doc8_values}});
    }
    ~ArrayValueFixture() override;
};

ArrayValueFixture::~ArrayValueFixture() = default;

TEST(ImportedSearchContextTest, non_strict_iterator_handles_unmapped_lids)
{
    ArrayValueFixture f;
    auto ctx = f.create_context(word_term("1234"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    EXPECT_FALSE(iter->seek(DocId(2)));
    EXPECT_EQ(iter->beginId(), iter->getDocId());
}

TEST(ImportedSearchContextTest, non_strict_iterator_handles_seek_outside_of_lid_space)
{
    ArrayValueFixture f;
    auto ctx = f.create_context(word_term("1234"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    const auto n_docs = f.reference_attr->getNumDocs();
    EXPECT_FALSE(iter->seek(DocId(n_docs + 1)));
    EXPECT_TRUE(iter->isAtEnd());
}

TEST(ImportedSearchContextTest, non_strict_iterator_unpacks_target_match_data_for_array_hit)
{
    ArrayValueFixture f;
    auto ctx = f.create_context(word_term("1234"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_hit_with_weight(*iter, match, DocId(1), 1));
    EXPECT_TRUE(is_hit_with_weight(*iter, match, DocId(4), 3));
}

struct WsetValueFixture : Fixture {
    WsetValueFixture() {
        std::vector<WeightedString> doc3_values{{WeightedString("foo", -5)}};
        std::vector<WeightedString> doc4_values{{WeightedString("baz", 10)}};
        std::vector<WeightedString> doc7_values{{WeightedString("bar", 7), WeightedString("foo", 42)}};
        reset_with_wset_value_reference_mappings<StringAttribute, WeightedString>(
                BasicType::STRING,
                {{DocId(2), dummy_gid(3), DocId(3), doc3_values},
                 {DocId(4), dummy_gid(4), DocId(4), doc4_values},
                 {DocId(6), dummy_gid(7), DocId(7), doc7_values}});
    }
    ~WsetValueFixture() override;
};

WsetValueFixture::~WsetValueFixture() = default;

TEST(ImportedSearchContextTest, non_strict_iterator_unpacks_target_match_data_for_weighted_set_hit)
{
    WsetValueFixture f;
    auto ctx = f.create_context(word_term("foo"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_hit_with_weight(*iter, match, DocId(2), -5));
    EXPECT_TRUE(is_hit_with_weight(*iter, match, DocId(6), 42));
}

TEST(ImportedSearchContextTest, strict_iterator_is_marked_as_strict)
{
    Fixture f;
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(iter->is_strict() == Trinary::True); // No EXPECT_EQUALS printing of Trinary...
}

TEST(ImportedSearchContextTest, non_strict_blueprint_with_high_hit_rate_is_strict)
{
    Fixture f(false, FastSearchConfig::ExplicitlyEnabled);
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::createForTest(0.02), false);
    TermFieldMatchData match;
    auto iter = f.create_iterator(*ctx, match, false);

    EXPECT_TRUE(iter->is_strict() == Trinary::True);
}

TEST(ImportedSearchContextTest, non_strict_blueprint_with_low_hit_rate_is_non_strict)
{
    Fixture f(false, FastSearchConfig::ExplicitlyEnabled);
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::createForTest(0.01), false);
    TermFieldMatchData match;
    auto iter = f.create_iterator(*ctx, match, false);

    EXPECT_TRUE(iter->is_strict() == Trinary::False);
}

struct SingleValueFixture : Fixture {
    SingleValueFixture() {
        reset_with_single_value_reference_mappings<IntegerAttribute, int32_t>(
                BasicType::INT32,
                {{DocId(3), dummy_gid(5), DocId(5), 5678},
                 {DocId(4), dummy_gid(6), DocId(6), 1234},
                 {DocId(5), dummy_gid(8), DocId(8), 5678},
                 {DocId(7), dummy_gid(9), DocId(9), 4321}});
    }
    ~SingleValueFixture() override;
};

SingleValueFixture::~SingleValueFixture() = default;

// Strict iteration implicitly tests unmapped LIDs by its nature, so we don't have a separate test for that.

TEST(ImportedSearchContextTest, strict_iterator_seeks_to_first_available_hit_lid)
{
    SingleValueFixture f;
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_FALSE(iter->isAtEnd());
    EXPECT_EQ(DocId(3), iter->getDocId());

    EXPECT_FALSE(iter->seek(DocId(1)));
    EXPECT_FALSE(iter->isAtEnd());
    EXPECT_EQ(DocId(3), iter->getDocId());

    EXPECT_TRUE(iter->seek(DocId(3)));
    EXPECT_FALSE(iter->isAtEnd());
    EXPECT_EQ(DocId(3), iter->getDocId());

    EXPECT_FALSE(iter->seek(DocId(4)));
    EXPECT_FALSE(iter->isAtEnd());
    EXPECT_EQ(DocId(5), iter->getDocId());

    // Seeking beyond last hit exhausts doc id limit and marks iterator as done
    EXPECT_FALSE(iter->seek(DocId(6)));
    EXPECT_TRUE(iter->isAtEnd());
}

TEST(ImportedSearchContextTest, strict_iterator_unpacks_target_match_data_for_single_value_hit)
{
    SingleValueFixture f;
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(1), DocId(3), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(2), DocId(3), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(3), DocId(3), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(4), DocId(5), 1));
}

TEST(ImportedSearchContextTest, strict_iterator_unpacks_target_match_data_for_array_hit)
{
    ArrayValueFixture f;
    auto ctx = f.create_context(word_term("1234"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(1), DocId(1), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(2), DocId(4), 3));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(3), DocId(4), 3));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(4), DocId(4), 3));
}

TEST(ImportedSearchContextTest, strict_iterator_unpacks_target_match_data_for_weighted_set_hit)
{
    WsetValueFixture f;
    auto ctx = f.create_context(word_term("foo"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(1), DocId(2), -5));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(2), DocId(2), -5));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(3), DocId(6), 42));
}

TEST(ImportedSearchContextTest, strict_iterator_handles_seek_outside_of_lid_space)
{
    ArrayValueFixture f;
    auto ctx = f.create_context(word_term("1234"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    const auto n_docs = f.reference_attr->getNumDocs();
    EXPECT_FALSE(iter->seek(DocId(n_docs + 1)));
    EXPECT_TRUE(iter->isAtEnd());
}

TEST(ImportedSearchContextTest, matches_performs_gid_mapping_and_forwards_to_target_attribute)
{
    SingleValueFixture f;
    auto ctx = f.create_context(word_term("5678"));
    EXPECT_FALSE(ctx->matches(DocId(2)));
    EXPECT_TRUE(ctx->matches(DocId(3)));
    EXPECT_FALSE(ctx->matches(DocId(4)));
    EXPECT_TRUE(ctx->matches(DocId(5)));
}

TEST(ImportedSearchContextTest, matches_weight_performs_gid_mapping_and_forwards_to_target_attribute)
{
    WsetValueFixture f;
    auto ctx = f.create_context(word_term("foo"));
    int32_t weight = 0;
    EXPECT_FALSE(ctx->matches(DocId(1), weight));
    EXPECT_EQ(0, weight); // Unchanged

    EXPECT_TRUE(ctx->matches(DocId(2), weight));
    EXPECT_EQ(-5, weight);

    EXPECT_TRUE(ctx->matches(DocId(6), weight));
    EXPECT_EQ(42, weight);
}

TEST(ImportedSearchContextTest, multiple_iterators_can_be_created_from_the_same_context)
{
    SingleValueFixture f;
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);

    TermFieldMatchData match1;
    auto iter1 = f.create_strict_iterator(*ctx, match1);

    TermFieldMatchData match2;
    auto iter2 = f.create_non_strict_iterator(*ctx, match2);

    TermFieldMatchData match3;
    auto iter3 = f.create_strict_iterator(*ctx, match3);

    TermFieldMatchData match4;
    auto iter4 = f.create_non_strict_iterator(*ctx, match4);

    EXPECT_TRUE(is_strict_hit_with_weight(*iter3, match3, DocId(4), DocId(5), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter1, match1, DocId(1), DocId(3), 1));
    EXPECT_TRUE(is_hit_with_weight(*iter4, match4, DocId(5), 1));
    EXPECT_TRUE(is_hit_with_weight(*iter2, match2, DocId(3), 1));
}

TEST(ImportedSearchContextTest, original_lid_range_is_used_by_search_context)
{
    SingleValueFixture f;
    auto first_ctx = f.create_context(word_term("5678"));
    add_n_docs_with_undefined_values(*f.reference_attr, 1);
    f.map_reference(DocId(10), dummy_gid(5), DocId(5));
    auto second_ctx = f.create_context(word_term("5678"));
    EXPECT_FALSE(first_ctx->matches(DocId(10)));
    EXPECT_TRUE(second_ctx->matches(DocId(10)));
} 

TEST(ImportedSearchContextTest, original_target_lid_range_is_used_by_search_context)
{
    SingleValueFixture f;
    EXPECT_EQ(11u, f.target_attr->getNumDocs());
    auto first_ctx = f.create_context(word_term("2345"));
    add_n_docs_with_undefined_values(*f.target_attr, 1);
    EXPECT_EQ(12u, f.target_attr->getNumDocs());
    auto typed_target_attr = f.template target_attr_as<IntegerAttribute>();
    ASSERT_TRUE(typed_target_attr->update(11, 2345));
    f.target_attr->commit();
    f.map_reference(DocId(8), dummy_gid(11), DocId(11));
    auto second_ctx = f.create_context(word_term("2345"));
    EXPECT_FALSE(first_ctx->matches(DocId(8)));
    EXPECT_TRUE(second_ctx->matches(DocId(8)));
}

// Note: this uses an underlying string attribute, as queryTerm() does not seem to
// implemented at all for (single) numeric attributes. Intentional?
TEST(ImportedSearchContextTest, queryTerm_returns_term_context_was_created_with)
{
    WsetValueFixture f;
    auto ctx = f.create_context(word_term("helloworld"));
    EXPECT_EQ(std::string("helloworld"), std::string(ctx->queryTerm()->getTerm()));
}

struct SearchCacheFixture : Fixture {
    SearchCacheFixture(FilterConfig filter_config = FilterConfig::ExplicitlyEnabled)
        : Fixture(true)
    {
        reset_with_wset_value_reference_mappings<IntegerAttribute, WeightedInt>(
                BasicType::INT32,
                {{DocId(3), dummy_gid(5), DocId(5), {{5678, 5}}},
                 {DocId(4), dummy_gid(6), DocId(6), {{1234, 7}}},
                 {DocId(5), dummy_gid(8), DocId(8), {{5678, 9}}},
                 {DocId(7), dummy_gid(9), DocId(9), {{4321, 11}}}},
                FastSearchConfig::ExplicitlyEnabled,
                filter_config);
    }
    ~SearchCacheFixture() override;
};

SearchCacheFixture::~SearchCacheFixture() = default;

std::shared_ptr<BitVectorSearchCache::Entry>
makeSearchCacheEntry(const std::vector<uint32_t> docIds, uint32_t docIdLimit)
{
    std::shared_ptr<BitVector> bitVector = BitVector::create(docIdLimit);
    for (uint32_t docId : docIds) {
        bitVector->setBit(docId);
    }
    return std::make_shared<BitVectorSearchCache::Entry>(IDocumentMetaStoreContext::IReadGuard::SP(), bitVector, docIdLimit);
}

TEST(ImportedSearchContextTest, bitvector_from_search_cache_is_used_if_found)
{
    SearchCacheFixture f;
    f.imported_attr->getSearchCache()->insert("5678",
                                              makeSearchCacheEntry({2, 6}, f.get_imported_attr()->getNumDocs()));
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);
    EXPECT_EQ(SimpleResult({2, 6}), f.search(*iter)); // Note: would be {3, 5} if cache was not used
    EXPECT_EQ(0u, f.document_meta_store->get_read_guard_cnt);
}

std::vector<uint32_t>
get_bitvector_hits(const BitVector &bitVector)
{
    std::vector<uint32_t> actDocsIds;
    bitVector.foreach_truebit([&](uint32_t docId){ actDocsIds.push_back(docId); });
    return actDocsIds;
}

void test_search_cache(bool increase_child_lidspace, FilterConfig filter_config)
{
    SearchCacheFixture f(filter_config);
    if (increase_child_lidspace) {
        f.reference_attr->addDocs(2 * ImportedSearchContext::bitvector_limit_divisor);
        f.reference_attr->commit();
    }
    EXPECT_EQ(0u, f.imported_attr->getSearchCache()->size());
    auto old_mem_usage = f.imported_attr->get_memory_usage();
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);
    EXPECT_EQ(SimpleResult({3, 5}), f.search(*iter));
    iter->initFullRange();
    EXPECT_FALSE(iter->seek(1));
    EXPECT_TRUE(iter->seek(3));
    iter->unpack(3);
    EXPECT_TRUE(match.has_ranking_data(3));
    if (filter_config == FilterConfig::ExplicitlyEnabled) {
        EXPECT_EQ(1, match.getWeight());
    } else {
        EXPECT_EQ(5, match.getWeight());
    }

    if (increase_child_lidspace || filter_config == FilterConfig::Default) {
        // weighted array
        EXPECT_EQ(0u, f.imported_attr->getSearchCache()->size());
        EXPECT_EQ(1u, f.document_meta_store->get_read_guard_cnt);
    } else {
        // bitvector
        EXPECT_EQ(1u, f.imported_attr->getSearchCache()->size());
        auto new_mem_usage = f.imported_attr->get_memory_usage();
        EXPECT_LT(old_mem_usage.usedBytes(), new_mem_usage.usedBytes());
        EXPECT_LT(old_mem_usage.allocatedBytes(), new_mem_usage.allocatedBytes());
        auto cacheEntry = f.imported_attr->getSearchCache()->find("5678");
        EXPECT_EQ(cacheEntry->docIdLimit, f.get_imported_attr()->getNumDocs());
        EXPECT_EQ((std::vector<uint32_t>{3, 5}), get_bitvector_hits(*cacheEntry->bitVector));
        EXPECT_EQ(1u, f.document_meta_store->get_read_guard_cnt);
    }
}

TEST(ImportedSearchContextTest, entry_is_inserted_into_search_cache_if_bit_vector_posting_list_is_used) {
    test_search_cache(false, FilterConfig::ExplicitlyEnabled);
}

TEST(ImportedSearchContextTest, entry_is_not_inserted_into_search_cache_if_weighted_array_posting_list_is_used) {
    test_search_cache(true, FilterConfig::ExplicitlyEnabled);
}

TEST(ImportedSearchContextTest, entry_is_not_inserted_into_search_cache_if_not_filter) {
    test_search_cache(false, FilterConfig::Default);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
