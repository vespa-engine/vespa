// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/monitoring_search_iterator.h>
#include <vespa/searchlib/queryeval/monitoring_dump_iterator.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/simplesearch.h>
#include <vespa/searchlib/queryeval/test/searchhistory.h>
#include <vespa/vespalib/objects/objectdumper.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::queryeval;
using namespace search::queryeval::test;
using namespace search::fef;
using search::BitVector;
using search::BitVectorIterator;
using std::make_unique;

struct HistorySearchIterator : public SearchIterator
{
    SearchHistory _history;
    mutable bool  _getPostingInfoCalled;
    HistorySearchIterator() : _history(), _getPostingInfoCalled(false) {}
    ~HistorySearchIterator() override;
    void doSeek(uint32_t docId) override {
        _history.seek("x", docId);
        setDocId(docId);
    }
    void doUnpack(uint32_t docId) override { _history.unpack("x", docId); }
    const PostingInfo *getPostingInfo() const override {
        _getPostingInfoCalled = true;
        return nullptr;
    }
};

HistorySearchIterator::~HistorySearchIterator() = default;

struct SimpleFixture
{
    MonitoringSearchIterator _itr;
    SimpleResult             _res;
    SimpleFixture()
        : _itr("SimpleIterator",
             SearchIterator::UP(new SimpleSearch(SimpleResult().addHit(2).addHit(4).addHit(8))),
             false),
        _res()
    {
        _res.search(_itr, 10);
    }
    ~SimpleFixture();
};

SimpleFixture::~SimpleFixture() = default;

struct AdvancedFixture
{
    MonitoringSearchIterator _itr;
    AdvancedFixture()
        : _itr("AdvancedIterator",
             SearchIterator::UP(new SimpleSearch(SimpleResult().addHit(2).addHit(4).addHit(8).
                                                 addHit(16).addHit(32).addHit(64).addHit(128))),
             true)
    {
    }
};

struct HistoryFixture
{
    MonitoringSearchIterator _itr;
    HistoryFixture()
        : _itr("HistoryIterator", SearchIterator::UP(new HistorySearchIterator()), false)
    {
    }
};

struct TreeFixture
{
    MonitoringSearchIterator::UP _itr;
    SimpleResult                 _res;
    TreeFixture()
        : _itr()
    {
        MultiSearch::Children children;
        children.emplace_back(
                new MonitoringSearchIterator("child1",
                                             SearchIterator::UP
                                             (new SimpleSearch(SimpleResult().addHit(2).addHit(4).addHit(6))),
                                             false));
        children.emplace_back(
                new MonitoringSearchIterator("child2",
                                             SearchIterator::UP
                                             (new SimpleSearch(SimpleResult().addHit(3).addHit(4).addHit(5))),
                                                        false));
        _itr.reset(new MonitoringSearchIterator("and",
                                                SearchIterator::UP(AndSearch::create(std::move(children), true)),
                                                false));
        _res.search(*_itr, 10);
    }
    ~TreeFixture();
};

TreeFixture::~TreeFixture() = default;

TEST(MonitoringSearchIteratorTest, require_that_number_of_seeks_is_collected)
{
    SimpleFixture f;
    EXPECT_EQ(4u, f._itr.getStats().getNumSeeks());
    EXPECT_EQ(4.0 / 3.0, f._itr.getStats().getNumSeeksPerUnpack());
}

TEST(MonitoringSearchIteratorTest, require_that_number_of_unpacks_is_collected)
{
    SimpleFixture f;
    EXPECT_EQ(3u, f._itr.getStats().getNumUnpacks());
}

TEST(MonitoringSearchIteratorTest, require_that_docid_stepping_is_collected_for_root_iterator)
{
    SimpleFixture f;
    EXPECT_EQ(4u, f._itr.getStats().getNumDocIdSteps());
    EXPECT_EQ(1, f._itr.getStats().getAvgDocIdSteps());
}

TEST(MonitoringSearchIteratorTest, require_that_docid_stepping_is_collected_for_child_iterator)
{
    AdvancedFixture f;
    f._itr.seek(1); // 2 - 1
    EXPECT_EQ(1u, f._itr.getStats().getNumDocIdSteps());
    f._itr.seek(19); // 19 - 2
    EXPECT_EQ(18u, f._itr.getStats().getNumDocIdSteps());
    f._itr.seek(64); // 64 - 32
    EXPECT_EQ(50u, f._itr.getStats().getNumDocIdSteps());
    f._itr.seek(74); // 74 - 64
    EXPECT_EQ(60u, f._itr.getStats().getNumDocIdSteps());
    EXPECT_EQ(60 / 4, f._itr.getStats().getAvgDocIdSteps());
}

TEST(MonitoringSearchIteratorTest, require_that_hit_skipping_is_collected)
{
    AdvancedFixture f;
    f._itr.seek(1);
    EXPECT_EQ(0u, f._itr.getStats().getNumHitSkips());
    f._itr.seek(4);
    EXPECT_EQ(0u, f._itr.getStats().getNumHitSkips());
    f._itr.seek(16);
    EXPECT_EQ(1u, f._itr.getStats().getNumHitSkips());
    f._itr.seek(120);
    EXPECT_EQ(3u, f._itr.getStats().getNumHitSkips());
    EXPECT_EQ(3.0 / 4.0, f._itr.getStats().getAvgHitSkips());
}

TEST(MonitoringSearchIteratorTest, require_that_results_from_underlying_iterator_is_exposed_through_monitoring_iterator)
{
    SimpleFixture f;
    EXPECT_EQ(SimpleResult().addHit(2).addHit(4).addHit(8), f._res);
}

TEST(MonitoringSearchIteratorTest, require_that_calls_are_forwarded_to_underlying_iterator)
{
    HistoryFixture f;
    f._itr.seek(2);
    EXPECT_EQ(2u, f._itr.getDocId());
    f._itr.unpack(2);
    f._itr.seek(4);
    EXPECT_EQ(4u, f._itr.getDocId());
    f._itr.unpack(4);
    f._itr.seek(8);
    EXPECT_EQ(8u, f._itr.getDocId());
    f._itr.unpack(8);
    f._itr.getPostingInfo();
    const HistorySearchIterator &hsi = dynamic_cast<const HistorySearchIterator &>(f._itr.getIterator());
    EXPECT_EQ(SearchHistory().seek("x", 2).unpack("x", 2).seek("x", 4).unpack("x", 4).seek("x", 8).unpack("x", 8),
              hsi._history);
    EXPECT_TRUE(hsi._getPostingInfoCalled);
}

void
addIterator(MonitoringSearchIterator::Dumper &d,
            const std::string &name,
            int64_t numSeeks,
            double avgDocIdSteps,
            double avgHitSkips,
            int64_t numUnpacks,
            double numSeeksPerUnpack)
{
    d.openStruct("void", "search::queryeval::MonitoringSearchIterator");
    d.visitString("iteratorName", name);
    {
        d.openStruct("void", "MonitoringSearchIterator::Stats");
        d.visitInt("numSeeks", numSeeks);
        d.visitFloat("avgDocIdSteps", avgDocIdSteps);
        d.visitFloat("avgHitSkips", avgHitSkips);
        d.visitInt("numUnpacks", numUnpacks);
        d.visitFloat("numSeeksPerUnpack", numSeeksPerUnpack);
        d.closeStruct();
    }
    d.closeStruct();
}

TEST(MonitoringSearchIteratorTest, require_that_dumper_can_handle_formatting_on_several_levels)
{
    MonitoringSearchIterator::Dumper d(2, 6, 6, 10, 3);
    addIterator(d, "root", 1, 1.1, 11.22, 11, 111.3);
    {
        d.openStruct("children", "void");
        addIterator(d, "c.1", 222222, 2.1111, 22.2222, 222000, 222.4444);
        {
            d.openStruct("children", "void");
            addIterator(d, "c.1.1", 333333, 3.1111, 33.2222, 333000, 333333.4444);
            addIterator(d, "c.1.2", 444, 4.22, 4.33, 440, 4.44);
            d.closeStruct();
        }
        addIterator(d, "c.2", 555, 5.22, 5.33, 550, 5.44);
        {
            d.openStruct("children", "void");
            addIterator(d, "c.2.1", 666666, 6.1111, 66.2222, 333000, 666666.4444);
            addIterator(d, "c.2.2", 777, 7.22, 7.33, 770, 7.44);
            d.closeStruct();
        }
        d.closeStruct();
    }
    EXPECT_EQ(
    "root:        1 seeks,      1.100 steps/seek,     11.220 skips/seek,     11 unpacks,    111.300 seeks/unpack\n"
    "  c.1:    222222 seeks,      2.111 steps/seek,     22.222 skips/seek, 222000 unpacks,    222.444 seeks/unpack\n"
    "    c.1.1:  333333 seeks,      3.111 steps/seek,     33.222 skips/seek, 333000 unpacks, 333333.444 seeks/unpack\n"
    "    c.1.2:     444 seeks,      4.220 steps/seek,      4.330 skips/seek,    440 unpacks,      4.440 seeks/unpack\n"
    "  c.2:       555 seeks,      5.220 steps/seek,      5.330 skips/seek,    550 unpacks,      5.440 seeks/unpack\n"
    "    c.2.1:  666666 seeks,      6.111 steps/seek,     66.222 skips/seek, 333000 unpacks, 666666.444 seeks/unpack\n"
    "    c.2.2:     777 seeks,      7.220 steps/seek,      7.330 skips/seek,    770 unpacks,      7.440 seeks/unpack\n",
    d.toString());
}

TEST(MonitoringSearchIteratorTest, require_that_single_iterator_can_be_dumped_compact)
{
    AdvancedFixture f;
    f._itr.seek(6);
    f._itr.seek(16);
    f._itr.unpack(16);
    MonitoringSearchIterator::Dumper dumper;
    visit(dumper, "", f._itr);
    EXPECT_EQ("AdvancedIterator: 2 seeks, 7.00 steps/seek, 1.00 skips/seek, 1 unpacks, 2.00 seeks/unpack\n",
              dumper.toString());
}

TEST(MonitoringSearchIteratorTest, require_that_iterator_tree_can_be_dumped_compact)
{
    TreeFixture f;
    MonitoringSearchIterator::Dumper dumper;
    visit(dumper, "", f._itr.get());
    EXPECT_EQ("and: 2 seeks, 1.00 steps/seek, 0.00 skips/seek, 1 unpacks, 2.00 seeks/unpack\n"
              "    child1: 3 seeks, 1.00 steps/seek, 0.00 skips/seek, 1 unpacks, 3.00 seeks/unpack\n"
              "    child2: 3 seeks, 1.67 steps/seek, 0.00 skips/seek, 1 unpacks, 3.00 seeks/unpack\n",
              dumper.toString());
}

TEST(MonitoringSearchIteratorTest, require_that_single_iterator_can_be_dumped_verbosely)
{
    AdvancedFixture f;
    f._itr.seek(6);
    f._itr.seek(16);
    f._itr.unpack(16);
    vespalib::ObjectDumper dumper;
    visit(dumper, "", &f._itr);
    EXPECT_EQ("search::queryeval::MonitoringSearchIterator {\n"
              "    iteratorName: 'AdvancedIterator'\n"
              "    iteratorType: 'search::queryeval::SimpleSearch'\n"
              "    stats: MonitoringSearchIterator::Stats {\n"
              "        numSeeks: 2\n"
              "        numDocIdSteps: 14\n"
              "        avgDocIdSteps: 7\n"
              "        numHitSkips: 2\n"
              "        avgHitSkips: 1\n"
              "        numUnpacks: 1\n"
              "        numSeeksPerUnpack: 2\n"
              "    }\n"
              "    tag: '<null>'\n"
              "}\n",
              dumper.toString());
}

TEST(MonitoringSearchIteratorTest, require_that_iterator_tree_can_be_dumped_verbosely)
{
    TreeFixture f;
    vespalib::ObjectDumper dumper;
    visit(dumper, "", f._itr.get());
    EXPECT_EQ("search::queryeval::MonitoringSearchIterator {\n"
              "    iteratorName: 'and'\n"
              "    iteratorType: 'search::queryeval::AndSearchStrict<search::queryeval::(anonymous namespace)::FullUnpack>'\n"
              "    stats: MonitoringSearchIterator::Stats {\n"
              "        numSeeks: 2\n"
              "        numDocIdSteps: 2\n"
              "        avgDocIdSteps: 1\n"
              "        numHitSkips: 0\n"
              "        avgHitSkips: 0\n"
              "        numUnpacks: 1\n"
              "        numSeeksPerUnpack: 2\n"
              "    }\n"
              "    children: std::vector {\n"
              "        [0]: search::queryeval::MonitoringSearchIterator {\n"
              "            iteratorName: 'child1'\n"
              "            iteratorType: 'search::queryeval::SimpleSearch'\n"
              "            stats: MonitoringSearchIterator::Stats {\n"
              "                numSeeks: 3\n"
              "                numDocIdSteps: 3\n"
              "                avgDocIdSteps: 1\n"
              "                numHitSkips: 0\n"
              "                avgHitSkips: 0\n"
              "                numUnpacks: 1\n"
              "                numSeeksPerUnpack: 3\n"
              "            }\n"
              "            tag: '<null>'\n"
              "        }\n"
              "        [1]: search::queryeval::MonitoringSearchIterator {\n"
              "            iteratorName: 'child2'\n"
              "            iteratorType: 'search::queryeval::SimpleSearch'\n"
              "            stats: MonitoringSearchIterator::Stats {\n"
              "                numSeeks: 3\n"
              "                numDocIdSteps: 5\n"
              "                avgDocIdSteps: 1.66667\n"
              "                numHitSkips: 0\n"
              "                avgHitSkips: 0\n"
              "                numUnpacks: 1\n"
              "                numSeeksPerUnpack: 3\n"
              "            }\n"
              "            tag: '<null>'\n"
              "        }\n"
              "    }\n"
              "}\n",
              dumper.toString());
}

class MonitoringSearchIteratorVerifier : public search::test::SearchIteratorVerifier {
public:
    SearchIterator::UP create(bool strict) const override {
        return createMonitoring(strict);
    }

protected:
    std::unique_ptr<MonitoringSearchIterator> createMonitoring(bool strict) const {
        return std::make_unique<MonitoringSearchIterator>("test", createIterator(getExpectedDocIds(), strict), false);

    }
};

class MonitoringDumpIteratorVerifier : public MonitoringSearchIteratorVerifier {
public:
    SearchIterator::UP create(bool strict) const override {
        return std::make_unique<MonitoringDumpIterator>(createMonitoring(strict));
    }
};

TEST(MonitoringSearchIteratorTest, test_monitoring_search_iterator_adheres_to_search_iterator_requirements)
{
    MonitoringSearchIteratorVerifier searchVerifier;
    searchVerifier.verify();
    MonitoringDumpIteratorVerifier dumpVerifier;
    dumpVerifier.verify();
}


GTEST_MAIN_RUN_ALL_TESTS()
