// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for sourceselector.

#include <vespa/searchlib/attribute/fixedsourceselector.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>
#include <filesystem>

using std::unique_ptr;
using std::string;
using namespace search;
using namespace search::queryeval;
using search::common::FileHeaderContext;
using search::index::DummyFileHeaderContext;

namespace {
template <typename T, size_t N> size_t arraysize(const T (&)[N]) { return N; }

const uint32_t maxDocId = 4_Ki;
struct DocSource { uint32_t docId; uint8_t source; };
const DocSource docs[] = { {0,1}, {1, 0}, {2, 2}, {4, 3}, {8, 9}, {16, 178},
                           {32, 1}, {64, 2}, {128, 3}, {256,4}, {512, 2},
                           {1024, 1}, {2048,5}, {maxDocId,1} };
const string index_dir = "test_data";
const string base_file_name = "test_data/sourcelist";
const string base_file_name2 = "test_data/sourcelist2";
const uint32_t default_source = 7;
const uint32_t invalid_source = (uint8_t)search::attribute::getUndefined<int8_t>();
const uint32_t base_id = 42;

uint8_t capSource(uint8_t source, uint8_t defaultSource, bool cap) {
    return (cap ? std::min(source, defaultSource) : source);
}

class SourceSelectorTest : public ::testing::Test
{
protected:
    SourceSelectorTest();
    ~SourceSelectorTest() override;
    void testSourceSelector(const DocSource *docSource, size_t sz, uint8_t defaultSource, ISourceSelector & selector, bool cap);
    void requireThatSelectorCanSaveAndLoad(bool compactLidSpace);
};

SourceSelectorTest::SourceSelectorTest() = default;
SourceSelectorTest::~SourceSelectorTest() = default;

void setSources(ISourceSelector &selector) {
    for (size_t i = 0; i < arraysize(docs); ++i) {
        selector.setSource(docs[i].docId, docs[i].source);
    }
}

TEST_F(SourceSelectorTest, test_fixed)
{
    const DocSource *docSource = docs;
    size_t sz = arraysize(docs);
    FixedSourceSelector selector(default_source, base_file_name, 10);
    EXPECT_EQ(default_source, selector.getDefaultSource());
    EXPECT_EQ(10u, selector.getDocIdLimit());
    setSources(selector);
    /*
     * One extra element beyond highest explicitly set element is
     * initialized to accommodate a match loop optimization. See
     * setSource() and reserve() member functions in
     * FixedSourceSelector for details.
     */
    EXPECT_EQ(default_source, selector.createIterator()->getSource(maxDocId + 1));
    testSourceSelector(docSource, sz, selector.getDefaultSource(), selector, false);
    EXPECT_EQ(maxDocId+1, selector.getDocIdLimit());
}

void
SourceSelectorTest::testSourceSelector(const DocSource *docSource, size_t sz,
                                       uint8_t defaultSource, ISourceSelector &selector, bool cap)
{
    {
        auto it(selector.createIterator());
        for (size_t i = 0; i < sz; ++i) {
            EXPECT_EQ((uint32_t)capSource(docSource[i].source, defaultSource, cap), (uint32_t)it->getSource(docSource[i].docId));
        }
    }
    {
        auto it(selector.createIterator());
        for (size_t i = 0, j = 0; i <= docSource[sz - 1].docId; ++i) {
            if (i != docSource[j].docId) {
                EXPECT_EQ((uint32_t)defaultSource, (uint32_t)it->getSource(i));
            } else {
                EXPECT_EQ((uint32_t)capSource(docSource[j].source, defaultSource, cap), (uint32_t)it->getSource(i));
                ++j;
            }
        }
    }
}

TEST_F(SourceSelectorTest, require_that_selector_can_clone_and_subtract)
{
    FixedSourceSelector selector(default_source, base_file_name);
    setSources(selector);
    selector.setBaseId(base_id);

    const uint32_t diff = 3;
    auto new_selector(selector.cloneAndSubtract(base_file_name2, diff));
    EXPECT_EQ(default_source - diff, new_selector->getDefaultSource());
    EXPECT_EQ(base_id + diff, new_selector->getBaseId());
    EXPECT_EQ(maxDocId+1, new_selector->getDocIdLimit());

    auto it(new_selector->createIterator());
    for(size_t i = 0; i < arraysize(docs); ++i) {
        if (docs[i].source > diff) {
            EXPECT_EQ(docs[i].source - diff, it->getSource(docs[i].docId));
        } else {
            EXPECT_EQ(0, it->getSource(docs[i].docId));
        }
    }
}

void
SourceSelectorTest::requireThatSelectorCanSaveAndLoad(bool compactLidSpace)
{
    SCOPED_TRACE(compactLidSpace ? "compactLidSpace=true" : "compactLidSpace=false");
    FixedSourceSelector selector(default_source, base_file_name2);
    setSources(selector);
    selector.setBaseId(base_id);
    selector.setSource(maxDocId + 1, default_source);
    if (compactLidSpace) {
        selector.compactLidSpace(maxDocId - 4);
    }

    std::filesystem::remove_all(std::filesystem::path(index_dir));
    std::filesystem::create_directory(std::filesystem::path(index_dir));

    SourceSelector::SaveInfo::UP save_info =
        selector.extractSaveInfo(base_file_name);
    save_info->save(TuneFileAttributes(), DummyFileHeaderContext());
    auto selector2(FixedSourceSelector::load(base_file_name, default_source + base_id));
    testSourceSelector(docs, arraysize(docs) - compactLidSpace, default_source, *selector2, true);
    EXPECT_EQ(base_id, selector2->getBaseId());
    if (compactLidSpace) {
        EXPECT_EQ(maxDocId - 4, selector2->getDocIdLimit());
    } else {
        EXPECT_EQ(maxDocId + 2, selector2->getDocIdLimit());
    }

    std::filesystem::remove_all(std::filesystem::path(index_dir));
}

TEST_F(SourceSelectorTest, require_that_selector_can_save_and_Load)
{
    requireThatSelectorCanSaveAndLoad(false);
    requireThatSelectorCanSaveAndLoad(true);
}

TEST_F(SourceSelectorTest, require_that_complete_source_range_is_handled)
{
    FixedSourceSelector selector(default_source, base_file_name);
    for (uint32_t i = 0; i < ISourceSelector::SOURCE_LIMIT; ++i) {
        selector.setSource(i, i);
    }
    auto itr = selector.createIterator();
    for (uint32_t i = 0; i < ISourceSelector::SOURCE_LIMIT; ++i) {
        EXPECT_EQ((queryeval::Source)i, itr->getSource(i));
    }
}

TEST_F(SourceSelectorTest, require_that_sources_are_counted_correctly)
{
    FixedSourceSelector selector(default_source, base_file_name);
    for (uint32_t i = 0; i < 256; ++i) {
        selector.setSource(i, i%16);
    }
    SourceSelector::Histogram hist = selector.getDistribution();
    for (uint32_t i = 0; i < 16; ++i) {
        EXPECT_EQ(16u, hist[i]);
    }
    for (uint32_t i = 16; i < 256; ++i) {
        EXPECT_EQ(0u, hist[i]);
    }
}

TEST_F(SourceSelectorTest, require_that_doc_id_limit_is_correct)
{
    FixedSourceSelector selector(default_source, base_file_name);
    EXPECT_EQ(0u, selector.getDocIdLimit());
    selector.setSource(8, 10);
    EXPECT_EQ(9u, selector.getDocIdLimit());
    selector.compactLidSpace(4);
    EXPECT_EQ(4u, selector.getDocIdLimit());
    selector.setSource(6, 10);
    EXPECT_EQ(7u, selector.getDocIdLimit());
    auto selector2 = selector.cloneAndSubtract(base_file_name2, 3);
    EXPECT_EQ(7u, selector2->getDocIdLimit());
}

TEST_F(SourceSelectorTest, require_that_correct_default_value_is_used_after_compaction)
{
    FixedSourceSelector selector(default_source, base_file_name);
    EXPECT_EQ(0u, selector.getDocIdLimit());
    auto it(selector.createIterator());
    selector.setSource(8, 4);
    EXPECT_EQ(default_source, (uint32_t) it->getSource(9));
    EXPECT_EQ(default_source, (uint32_t) it->getSource(6));
    selector.compactLidSpace(4);
    EXPECT_EQ(4u, selector.getDocIdLimit());
    EXPECT_EQ(default_source, (uint32_t) it->getSource(4));
    EXPECT_EQ(invalid_source, (uint32_t) it->getSource(5)); // beyond guard
    selector.setSource(6, 4);
    EXPECT_EQ(7u, selector.getDocIdLimit());
    EXPECT_EQ(default_source, (uint32_t) it->getSource(5));
    EXPECT_EQ(4u, (uint8_t) it->getSource(6));
    EXPECT_EQ(default_source, (uint32_t) it->getSource(7));
}


}  // namespace

int
main(int argc, char* argv[])
{
    ::testing::InitGoogleTest(&argc, argv);
    if (argc > 0) {
        DummyFileHeaderContext::setCreator(argv[0]);
    }
    return RUN_ALL_TESTS();
}
