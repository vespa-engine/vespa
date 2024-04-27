// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchlib/attribute/imported_search_context.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/test/imported_attribute_fixture.h>
#include <vespa/searchlib/test/mock_gid_to_lid_mapping.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/searchlib/queryeval/executeinfo.h>

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

    void assertSearch(const std::vector<uint32_t> &expDocIds, SearchIterator &iter) {
        EXPECT_EQUAL(SimpleResult(expDocIds), SimpleResult().searchStrict(iter, get_imported_attr()->getNumDocs()));
    }
};

Fixture::~Fixture() = default;

template <typename Iterator>
bool is_hit_with_weight(Iterator& iter, TermFieldMatchData& match, DocId lid, int32_t weight) {
    if (!EXPECT_TRUE(iter.seek(lid))) {
        return false;
    }
    iter.unpack(lid);
    return (EXPECT_EQUAL(lid, match.getDocId()) &&
            EXPECT_EQUAL(weight, match.getWeight()));
}

template <typename Iterator>
bool is_strict_hit_with_weight(Iterator& iter, TermFieldMatchData& match,
                               DocId seek_lid, DocId expected_lid, int32_t weight) {
    iter.seek(seek_lid);
    if (!EXPECT_EQUAL(expected_lid, iter.getDocId())) {
        return false;
    }
    iter.unpack(expected_lid);
    return (EXPECT_EQUAL(expected_lid, match.getDocId()) &&
            EXPECT_EQUAL(weight, match.getWeight()));
}

TEST_F("calc_hit_estimate() returns document count of reference attribute when not using fast-search target attribute", Fixture) {
    add_n_docs_with_undefined_values(*f.target_attr, 10);
    add_n_docs_with_undefined_values(*f.reference_attr, 101);

    auto ctx = f.create_context(word_term("foo"));
    auto est = ctx->calc_hit_estimate();
    EXPECT_EQUAL(101u, est.est_hits());
    EXPECT_TRUE(est.is_unknown());
}

TEST_F("calc_hit_estimate() estimates hits when using fast-search target attribute", Fixture(false, FastSearchConfig::ExplicitlyEnabled))
{
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
    EXPECT_EQUAL(0u, est.est_hits());
    EXPECT_FALSE(est.is_unknown());
    TermFieldMatchData match;
    auto iter = f.create_iterator(*ctx, match, false);
    EXPECT_TRUE(iter->matches_any() == Trinary::False);
    ctx = f.create_context(word_term("20"));
    // Exact count: 2 target hits, 2 docs / target doc => 2 * 2 = 4
    est = ctx->calc_hit_estimate();
    EXPECT_EQUAL(4u, est.est_hits());
    EXPECT_FALSE(est.is_unknown());
    ctx = f.create_context(word_term("30"));
    // Approximation: 110 target hits => 110 * 10001 / 1001 = 1099
    est = ctx->calc_hit_estimate();
    EXPECT_EQUAL(1099u, est.est_hits());
    EXPECT_FALSE(est.is_unknown());
}

TEST_F("attributeName() returns imported attribute name", Fixture) {
    auto ctx = f.create_context(word_term("foo"));
    EXPECT_EQUAL(f.default_imported_attr_name(), ctx->attributeName());
}

TEST_F("valid() forwards to target search context", Fixture) {
    auto ctx = f.create_context(word_term("foo"));
    EXPECT_EQUAL(ctx->target_search_context().valid(), ctx->valid());
}

TEST_F("getAsIntegerTerm() forwards to target search context", Fixture) {
    auto ctx = f.create_context(word_term("foo"));
    // No operator== or printing for Range, so doing this the hard way
    auto expected_range = ctx->target_search_context().getAsIntegerTerm();
    auto actual_range = ctx->getAsIntegerTerm();
    EXPECT_EQUAL(expected_range.lower(), actual_range.lower());
    EXPECT_EQUAL(expected_range.upper(), actual_range.upper());
}

TEST_F("Non-strict iterator not marked as strict", Fixture) {
    auto ctx = f.create_context(word_term("5678"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    EXPECT_TRUE(iter->is_strict() == Trinary::False); // No EXPECT_EQUALS printing of Trinary...
}

TEST_F("Non-strict iterator seek forwards to target attribute", Fixture) {
    reset_with_single_value_reference_mappings<IntegerAttribute, int32_t>(
            f, BasicType::INT32,
            {{DocId(1), dummy_gid(3), DocId(3), 1234},
             {DocId(3), dummy_gid(7), DocId(7), 5678},
             {DocId(5), dummy_gid(8), DocId(8), 7890}});

    auto ctx = f.create_context(word_term("5678"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    EXPECT_FALSE(iter->isAtEnd());
    EXPECT_EQUAL(iter->beginId(), iter->getDocId());

    EXPECT_FALSE(iter->seek(DocId(1)));
    EXPECT_EQUAL(iter->beginId(), iter->getDocId()); // Non-strict iterator does not change current ID

    EXPECT_TRUE(iter->seek(DocId(3)));
    EXPECT_EQUAL(DocId(3), iter->getDocId());

    EXPECT_FALSE(iter->seek(DocId(5)));
    EXPECT_EQUAL(DocId(3), iter->getDocId()); // Still unchanged
}

TEST_F("Non-strict iterator unpacks target match data for single value hit", Fixture) {
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

TEST_F("Non-strict iterator handles unmapped LIDs", ArrayValueFixture) {
    auto ctx = f.create_context(word_term("1234"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    EXPECT_FALSE(iter->seek(DocId(2)));
    EXPECT_EQUAL(iter->beginId(), iter->getDocId());
}

TEST_F("Non-strict iterator handles seek outside of LID space", ArrayValueFixture) {
    auto ctx = f.create_context(word_term("1234"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    const auto n_docs = f.reference_attr->getNumDocs();
    EXPECT_FALSE(iter->seek(DocId(n_docs + 1)));
    EXPECT_TRUE(iter->isAtEnd());
}

TEST_F("Non-strict iterator unpacks target match data for array hit", ArrayValueFixture) {
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

TEST_F("Non-strict iterator unpacks target match data for weighted set hit", WsetValueFixture) {
    auto ctx = f.create_context(word_term("foo"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_hit_with_weight(*iter, match, DocId(2), -5));
    EXPECT_TRUE(is_hit_with_weight(*iter, match, DocId(6), 42));
}

TEST_F("Strict iterator is marked as strict", Fixture) {
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(iter->is_strict() == Trinary::True); // No EXPECT_EQUALS printing of Trinary...
}

TEST_F("Non-strict blueprint with high hit rate is strict", Fixture(false, FastSearchConfig::ExplicitlyEnabled)) {
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::createForTest(0.02), false);
    TermFieldMatchData match;
    auto iter = f.create_iterator(*ctx, match, false);

    EXPECT_TRUE(iter->is_strict() == Trinary::True);
}

TEST_F("Non-strict blueprint with low hit rate is non-strict", Fixture(false, FastSearchConfig::ExplicitlyEnabled)) {
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

TEST_F("Strict iterator seeks to first available hit LID", SingleValueFixture) {
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_FALSE(iter->isAtEnd());
    EXPECT_EQUAL(DocId(3), iter->getDocId());

    EXPECT_FALSE(iter->seek(DocId(1)));
    EXPECT_FALSE(iter->isAtEnd());
    EXPECT_EQUAL(DocId(3), iter->getDocId());

    EXPECT_TRUE(iter->seek(DocId(3)));
    EXPECT_FALSE(iter->isAtEnd());
    EXPECT_EQUAL(DocId(3), iter->getDocId());

    EXPECT_FALSE(iter->seek(DocId(4)));
    EXPECT_FALSE(iter->isAtEnd());
    EXPECT_EQUAL(DocId(5), iter->getDocId());

    // Seeking beyond last hit exhausts doc id limit and marks iterator as done
    EXPECT_FALSE(iter->seek(DocId(6)));
    EXPECT_TRUE(iter->isAtEnd());
}

TEST_F("Strict iterator unpacks target match data for single value hit", SingleValueFixture) {
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(1), DocId(3), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(2), DocId(3), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(3), DocId(3), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(4), DocId(5), 1));
}

TEST_F("Strict iterator unpacks target match data for array hit", ArrayValueFixture) {
    auto ctx = f.create_context(word_term("1234"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(1), DocId(1), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(2), DocId(4), 3));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(3), DocId(4), 3));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(4), DocId(4), 3));
}

TEST_F("Strict iterator unpacks target match data for weighted set hit", WsetValueFixture) {
    auto ctx = f.create_context(word_term("foo"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(1), DocId(2), -5));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(2), DocId(2), -5));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(3), DocId(6), 42));
}

TEST_F("Strict iterator handles seek outside of LID space", ArrayValueFixture) {
    auto ctx = f.create_context(word_term("1234"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    const auto n_docs = f.reference_attr->getNumDocs();
    EXPECT_FALSE(iter->seek(DocId(n_docs + 1)));
    EXPECT_TRUE(iter->isAtEnd());
}

TEST_F("matches() performs GID mapping and forwards to target attribute", SingleValueFixture) {
    auto ctx = f.create_context(word_term("5678"));
    EXPECT_FALSE(ctx->matches(DocId(2)));
    EXPECT_TRUE(ctx->matches(DocId(3)));
    EXPECT_FALSE(ctx->matches(DocId(4)));
    EXPECT_TRUE(ctx->matches(DocId(5)));
}

TEST_F("matches(weight) performs GID mapping and forwards to target attribute", WsetValueFixture) {
    auto ctx = f.create_context(word_term("foo"));
    int32_t weight = 0;
    EXPECT_FALSE(ctx->matches(DocId(1), weight));
    EXPECT_EQUAL(0, weight); // Unchanged

    EXPECT_TRUE(ctx->matches(DocId(2), weight));
    EXPECT_EQUAL(-5, weight);

    EXPECT_TRUE(ctx->matches(DocId(6), weight));
    EXPECT_EQUAL(42, weight);
}

TEST_F("Multiple iterators can be created from the same context", SingleValueFixture) {
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

TEST_F("original lid range is used by search context", SingleValueFixture)
{
    auto first_ctx = f.create_context(word_term("5678"));
    add_n_docs_with_undefined_values(*f.reference_attr, 1);
    f.map_reference(DocId(10), dummy_gid(5), DocId(5));
    auto second_ctx = f.create_context(word_term("5678"));
    EXPECT_FALSE(first_ctx->matches(DocId(10)));
    EXPECT_TRUE(second_ctx->matches(DocId(10)));
} 

TEST_F("Original target lid range is used by search context", SingleValueFixture)
{
    EXPECT_EQUAL(11u, f.target_attr->getNumDocs());
    auto first_ctx = f.create_context(word_term("2345"));
    add_n_docs_with_undefined_values(*f.target_attr, 1);
    EXPECT_EQUAL(12u, f.target_attr->getNumDocs());
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
TEST_F("queryTerm() returns term context was created with", WsetValueFixture) {
    auto ctx = f.create_context(word_term("helloworld"));
    EXPECT_EQUAL(std::string("helloworld"), std::string(ctx->queryTerm()->getTerm()));
}

struct SearchCacheFixture : Fixture {
    SearchCacheFixture() : Fixture(true) {
        reset_with_single_value_reference_mappings<IntegerAttribute, int32_t>(
                BasicType::INT32,
                {{DocId(3), dummy_gid(5), DocId(5), 5678},
                 {DocId(4), dummy_gid(6), DocId(6), 1234},
                 {DocId(5), dummy_gid(8), DocId(8), 5678},
                 {DocId(7), dummy_gid(9), DocId(9), 4321}},
                FastSearchConfig::ExplicitlyEnabled,
                FilterConfig::ExplicitlyEnabled);
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

TEST_F("Bit vector from search cache is used if found", SearchCacheFixture)
{
    f.imported_attr->getSearchCache()->insert("5678",
                                              makeSearchCacheEntry({2, 6}, f.get_imported_attr()->getNumDocs()));
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);
    TEST_DO(f.assertSearch({2, 6}, *iter)); // Note: would be {3, 5} if cache was not used
    EXPECT_EQUAL(0u, f.document_meta_store->get_read_guard_cnt);
}

void
assertBitVector(const std::vector<uint32_t> &expDocIds, const BitVector &bitVector)
{
    std::vector<uint32_t> actDocsIds;
    bitVector.foreach_truebit([&](uint32_t docId){ actDocsIds.push_back(docId); });
    EXPECT_EQUAL(expDocIds, actDocsIds);
}

TEST_F("Entry is inserted into search cache if bit vector posting list is used", SearchCacheFixture)
{
    EXPECT_EQUAL(0u, f.imported_attr->getSearchCache()->size());
    auto old_mem_usage = f.imported_attr->get_memory_usage();
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(queryeval::ExecuteInfo::FULL, true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);
    TEST_DO(f.assertSearch({3, 5}, *iter));

    EXPECT_EQUAL(1u, f.imported_attr->getSearchCache()->size());
    auto new_mem_usage = f.imported_attr->get_memory_usage();
    EXPECT_LESS(old_mem_usage.usedBytes(), new_mem_usage.usedBytes());
    EXPECT_LESS(old_mem_usage.allocatedBytes(), new_mem_usage.allocatedBytes());
    auto cacheEntry = f.imported_attr->getSearchCache()->find("5678");
    EXPECT_EQUAL(cacheEntry->docIdLimit, f.get_imported_attr()->getNumDocs());
    TEST_DO(assertBitVector({3, 5}, *cacheEntry->bitVector));
    EXPECT_EQUAL(1u, f.document_meta_store->get_read_guard_cnt);
}

}

TEST_MAIN() { TEST_RUN_ALL(); }
