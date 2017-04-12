// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for sourceselector.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("sourceselector_test");

#include <vespa/searchlib/attribute/fixedsourceselector.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/testkit/testapp.h>

using std::unique_ptr;
using std::string;
using namespace search;
using namespace search::queryeval;
using search::common::FileHeaderContext;
using search::index::DummyFileHeaderContext;

namespace {
template <typename T, size_t N> size_t arraysize(const T (&)[N]) { return N; }

const uint32_t maxDocId = 4096;
struct DocSource { uint32_t docId; uint8_t source; };
const DocSource docs[] = { {0,1}, {1, 0}, {2, 2}, {4, 3}, {8, 9}, {16, 178},
                           {32, 1}, {64, 2}, {128, 3}, {256,4}, {512, 2},
                           {1024, 1}, {2048,5}, {maxDocId,1} };
const string index_dir = "test_data";
const string base_file_name = "test_data/sourcelist";
const string base_file_name2 = "test_data/sourcelist2";
const uint32_t default_source = 7;
const uint32_t base_id = 42;

class Test : public vespalib::TestApp
{
public:
    int Main() override;
private:
    void testSourceSelector(const DocSource *docSource, size_t sz, uint8_t defaultSource, ISourceSelector & selector);
    void testFixed(const DocSource *docSource, size_t sz);
    template <typename SelectorType>
    void requireThatSelectorCanCloneAndSubtract();
    void requireThatSelectorCanCloneAndSubtract();
    template <typename SelectorType>
    void requireThatSelectorCanSaveAndLoad();
    void requireThatSelectorCanSaveAndLoad();
    template <typename SelectorType>
    void requireThatCompleteSourceRangeIsHandled();
    void requireThatCompleteSourceRangeIsHandled();
    template <typename SelectorType>
    void requireThatSourcesAreCountedCorrectly();
    void requireThatSourcesAreCountedCorrectly();
};

int
Test::Main()
{
    TEST_INIT("sourceselector_test");

    if (_argc > 0) {
        DummyFileHeaderContext::setCreator(_argv[0]);
    }
    testFixed(docs, arraysize(docs));
    TEST_DO(requireThatSelectorCanCloneAndSubtract());
    TEST_DO(requireThatSelectorCanSaveAndLoad());
    TEST_DO(requireThatCompleteSourceRangeIsHandled());
    TEST_DO(requireThatSourcesAreCountedCorrectly());

    TEST_DONE();
}

void setSources(ISourceSelector &selector) {
    for (size_t i = 0; i < arraysize(docs); ++i) {
        selector.setSource(docs[i].docId, docs[i].source);
    }
}

void Test::testFixed(const DocSource *docSource, size_t sz)
{
    FixedSourceSelector selector(default_source, base_file_name, 10);
    EXPECT_EQUAL(default_source, selector.getDefaultSource());
    EXPECT_EQUAL(10u, selector.getDocIdLimit());
//    EXPECT_EQUAL(default_source, selector.createIterator()->getSource(maxDocId + 1));
    setSources(selector);
    testSourceSelector(docSource, sz, selector.getDefaultSource(), selector);
    EXPECT_EQUAL(maxDocId+1, selector.getDocIdLimit());
}

void Test::testSourceSelector(const DocSource *docSource, size_t sz,
                              uint8_t defaultSource, ISourceSelector &selector)
{
    {
        auto it(selector.createIterator());
        for (size_t i = 0; i < sz; ++i) {
            EXPECT_EQUAL(docSource[i].source, it->getSource(docSource[i].docId));
        }
    }
    {
        auto it(selector.createIterator());
        for (size_t i = 0, j = 0; i <= docSource[sz - 1].docId; ++i) {
            if (i != docSource[j].docId) {
                EXPECT_EQUAL(defaultSource, it->getSource(i));
            } else {
                EXPECT_EQUAL(docSource[j].source, it->getSource(i));
                ++j;
            }
        }
    }
}

template <typename SelectorType>
void
Test::requireThatSelectorCanCloneAndSubtract()
{
    SelectorType selector(default_source, base_file_name);
    setSources(selector);
    selector.setBaseId(base_id);

    const uint32_t diff = 3;
    typename SelectorType::UP
        new_selector(selector.cloneAndSubtract(base_file_name2, diff));
    EXPECT_EQUAL(default_source - diff, new_selector->getDefaultSource());
    EXPECT_EQUAL(base_id + diff, new_selector->getBaseId());
    EXPECT_EQUAL(maxDocId+1, new_selector->getDocIdLimit());

    auto it(new_selector->createIterator());
    for(size_t i = 0; i < arraysize(docs); ++i) {
        if (docs[i].source > diff) {
            EXPECT_EQUAL(docs[i].source - diff, it->getSource(docs[i].docId));
        } else {
            EXPECT_EQUAL(0, it->getSource(docs[i].docId));
        }
    }
}

void
Test::requireThatSelectorCanCloneAndSubtract()
{
    requireThatSelectorCanCloneAndSubtract<FixedSourceSelector>();
}

template <typename SelectorType>
void
Test::requireThatSelectorCanSaveAndLoad()
{
    SelectorType selector(default_source, base_file_name2);
    setSources(selector);
    selector.setBaseId(base_id);
    selector.setSource(maxDocId + 1, default_source);

    FastOS_FileInterface::EmptyAndRemoveDirectory(index_dir.c_str());
    FastOS_FileInterface::MakeDirIfNotPresentOrExit(index_dir.c_str());

    SourceSelector::SaveInfo::UP save_info =
        selector.extractSaveInfo(base_file_name);
    save_info->save(TuneFileAttributes(), DummyFileHeaderContext());
    typename SelectorType::UP
        selector2(SelectorType::load(base_file_name));
    testSourceSelector(docs, arraysize(docs), default_source, *selector2);
    EXPECT_EQUAL(base_id, selector2->getBaseId());
    EXPECT_EQUAL(maxDocId + 2, selector2->getDocIdLimit());

    FastOS_FileInterface::EmptyAndRemoveDirectory(index_dir.c_str());
}

void
Test::requireThatSelectorCanSaveAndLoad()
{
    requireThatSelectorCanSaveAndLoad<FixedSourceSelector>();
}

template <typename SelectorType>
void
Test::requireThatCompleteSourceRangeIsHandled()
{
    SelectorType selector(default_source, base_file_name);
    for (uint32_t i = 0; i < ISourceSelector::SOURCE_LIMIT; ++i) {
        selector.setSource(i, i);
    }
    auto itr = selector.createIterator();
    for (uint32_t i = 0; i < ISourceSelector::SOURCE_LIMIT; ++i) {
        EXPECT_EQUAL((queryeval::Source)i, itr->getSource(i));
    }
}

void
Test::requireThatCompleteSourceRangeIsHandled()
{
    requireThatCompleteSourceRangeIsHandled<FixedSourceSelector>();
}

template <typename SelectorType>
void
Test::requireThatSourcesAreCountedCorrectly()
{
    SelectorType selector(default_source, base_file_name);
    for (uint32_t i = 0; i < 256; ++i) {
        selector.setSource(i, i%16);
    }
    SourceSelector::Histogram hist = selector.getDistribution();
    for (uint32_t i = 0; i < 16; ++i) {
        EXPECT_EQUAL(16u, hist[i]);
    }
    for (uint32_t i = 16; i < 256; ++i) {
        EXPECT_EQUAL(0u, hist[i]);
    }
}

void
Test::requireThatSourcesAreCountedCorrectly()
{
    requireThatSourcesAreCountedCorrectly<FixedSourceSelector>();
}

}  // namespace

TEST_APPHOOK(Test);
