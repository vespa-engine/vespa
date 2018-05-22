// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/imported_search_context.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/test/imported_attribute_fixture.h>
#include <vespa/vespalib/test/insertion_operators.h>

namespace search::attribute {

using fef::TermFieldMatchData;
using queryeval::SearchIterator;
using queryeval::SimpleResult;
using vespalib::Trinary;

struct Fixture : ImportedAttributeFixture {

    Fixture(bool useSearchCache = false) : ImportedAttributeFixture(useSearchCache) {}

    std::unique_ptr<ImportedSearchContext> create_context(std::unique_ptr<QueryTermSimple> term) {
        return std::make_unique<ImportedSearchContext>(std::move(term), SearchContextParams(), *imported_attr, *target_attr);
    }

    std::unique_ptr<SearchIterator> create_iterator(
            ImportedSearchContext& ctx,
            TermFieldMatchData& match,
            bool strict) {
        auto iter = ctx.createIterator(&match, strict);
        assert(iter.get() != nullptr);
        iter->initRange(DocId(1), reference_attr->getNumDocs());
        return iter;
    }

    std::unique_ptr<SearchIterator> create_non_strict_iterator(
            ImportedSearchContext& ctx,
            TermFieldMatchData& match) {
        return create_iterator(ctx, match, false);
    }

    std::unique_ptr<SearchIterator> create_strict_iterator(
            ImportedSearchContext& ctx,
            TermFieldMatchData& match) {
        return create_iterator(ctx, match, true);
    }

    void assertSearch(const std::vector<uint32_t> &expDocIds, SearchIterator &iter) {
        EXPECT_EQUAL(SimpleResult(expDocIds), SimpleResult().searchStrict(iter, get_imported_attr()->getNumDocs()));
    }
};

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

TEST_F("approximateHits() returns document count of reference attribute", Fixture) {
    add_n_docs_with_undefined_values(*f.reference_attr, 101);

    auto ctx = f.create_context(word_term("foo"));
    EXPECT_EQUAL(101u, ctx->approximateHits());
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
};

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
};

TEST_F("Non-strict iterator unpacks target match data for weighted set hit", WsetValueFixture) {
    auto ctx = f.create_context(word_term("foo"));
    TermFieldMatchData match;
    auto iter = f.create_non_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_hit_with_weight(*iter, match, DocId(2), -5));
    EXPECT_TRUE(is_hit_with_weight(*iter, match, DocId(6), 42));
}

TEST_F("Strict iterator is marked as strict", Fixture) {
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(iter->is_strict() == Trinary::True); // No EXPECT_EQUALS printing of Trinary...
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
};

// Strict iteration implicitly tests unmapped LIDs by its nature, so we don't have a separate test for that.

TEST_F("Strict iterator seeks to first available hit LID", SingleValueFixture) {
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(true);
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
    ctx->fetchPostings(true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(1), DocId(3), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(2), DocId(3), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(3), DocId(3), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(4), DocId(5), 1));
}

TEST_F("Strict iterator unpacks target match data for array hit", ArrayValueFixture) {
    auto ctx = f.create_context(word_term("1234"));
    ctx->fetchPostings(true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(1), DocId(1), 1));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(2), DocId(4), 3));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(3), DocId(4), 3));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(4), DocId(4), 3));
}

TEST_F("Strict iterator unpacks target match data for weighted set hit", WsetValueFixture) {
    auto ctx = f.create_context(word_term("foo"));
    ctx->fetchPostings(true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(1), DocId(2), -5));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(2), DocId(2), -5));
    EXPECT_TRUE(is_strict_hit_with_weight(*iter, match, DocId(3), DocId(6), 42));
}

TEST_F("Strict iterator handles seek outside of LID space", ArrayValueFixture) {
    auto ctx = f.create_context(word_term("1234"));
    ctx->fetchPostings(true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);

    const auto n_docs = f.reference_attr->getNumDocs();
    EXPECT_FALSE(iter->seek(DocId(n_docs + 1)));
    EXPECT_TRUE(iter->isAtEnd());
}

TEST_F("cmp() performs GID mapping and forwards to target attribute", SingleValueFixture) {
    auto ctx = f.create_context(word_term("5678"));
    EXPECT_FALSE(ctx->matches(DocId(2)));
    EXPECT_TRUE(ctx->matches(DocId(3)));
    EXPECT_FALSE(ctx->matches(DocId(4)));
    EXPECT_TRUE(ctx->matches(DocId(5)));
}

TEST_F("cmp(weight) performs GID mapping and forwards to target attribute", WsetValueFixture) {
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
    ctx->fetchPostings(true);

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

// Note: this uses an underlying string attribute, as queryTerm() does not seem to
// implemented at all for (single) numeric attributes. Intentional?
TEST_F("queryTerm() returns term context was created with", WsetValueFixture) {
    auto ctx = f.create_context(word_term("helloworld"));
    EXPECT_EQUAL(std::string("helloworld"), std::string(ctx->queryTerm().getTerm()));
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
};

BitVectorSearchCache::Entry::SP
makeSearchCacheEntry(const std::vector<uint32_t> docIds, uint32_t docIdLimit)
{
    std::shared_ptr<BitVector> bitVector = BitVector::create(docIdLimit);
    for (uint32_t docId : docIds) {
        bitVector->setBit(docId);
    }
    return std::make_shared<BitVectorSearchCache::Entry>(IDocumentMetaStoreContext::IReadGuard::UP(), bitVector, docIdLimit);
}

TEST_F("Bit vector from search cache is used if found", SearchCacheFixture)
{
    f.imported_attr->getSearchCache()->insert("5678",
                                              makeSearchCacheEntry({2, 6}, f.get_imported_attr()->getNumDocs()));
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(true);
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
    auto ctx = f.create_context(word_term("5678"));
    ctx->fetchPostings(true);
    TermFieldMatchData match;
    auto iter = f.create_strict_iterator(*ctx, match);
    TEST_DO(f.assertSearch({3, 5}, *iter));

    EXPECT_EQUAL(1u, f.imported_attr->getSearchCache()->size());
    auto cacheEntry = f.imported_attr->getSearchCache()->find("5678");
    EXPECT_EQUAL(cacheEntry->docIdLimit, f.get_imported_attr()->getNumDocs());
    TEST_DO(assertBitVector({3, 5}, *cacheEntry->bitVector));
    EXPECT_EQUAL(1u, f.document_meta_store->get_read_guard_cnt);
}

}

TEST_MAIN() { TEST_RUN_ALL(); }
