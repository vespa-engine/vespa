// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("phrasesplitter_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/phrasesplitter.h>
#include <vespa/searchlib/fef/phrase_splitter_query_env.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>

namespace search {
namespace fef {

class PhraseSplitterTest : public vespalib::TestApp
{
private:
    void assertTermData(const ITermData * td, uint32_t uniqueId, uint32_t numTerms,
                        uint32_t fieldId, uint32_t termHandle);
    void testCopyTermFieldMatchData();
    void testSplitter();
    void testSplitterUpdate();

public:
    int Main() override;
};

void
PhraseSplitterTest::assertTermData(const ITermData *td, uint32_t uniqueId, uint32_t numTerms,
                                   uint32_t fieldId, uint32_t tfHandle)
{
    // fprintf(stderr, "checking uid=%d numterms=%d field=%d handle=%d\n", uniqueId, numTerms, fieldId, tfHandle);
    EXPECT_EQUAL(uniqueId, td->getUniqueId());
    EXPECT_EQUAL(numTerms, td->getPhraseLength());
    EXPECT_EQUAL(tfHandle, td->lookupField(fieldId)->getHandle());
}

void
PhraseSplitterTest::testCopyTermFieldMatchData()
{
    TermFieldMatchData src;
    src.reset(1);
    src.appendPosition(TermFieldMatchDataPosition(0, 5, 0, 1000));
    src.appendPosition(TermFieldMatchDataPosition(0, 15, 0, 1000));

    SimpleTermData td;
    TermFieldMatchData dst;
    dst.reset(0);
    // dst.setTermData(&td);
    dst.appendPosition(TermFieldMatchDataPosition(0, 10, 0, 1000));
    {
        FieldPositionsIterator itr = dst.getIterator();
        EXPECT_EQUAL(itr.getPosition(), 10u);
        itr.next();
        ASSERT_TRUE(!itr.valid());
    }

    PhraseSplitter::copyTermFieldMatchData(dst, src, 2);

    EXPECT_EQUAL(dst.getDocId(), 1u);
    {
        TermFieldMatchData::PositionsIterator itr = dst.begin();
        EXPECT_EQUAL(itr->getPosition(), 7u);
        ++itr;
        EXPECT_EQUAL(itr->getPosition(), 17u);
        ++itr;
        ASSERT_TRUE(itr == dst.end());
    }
    {
        FieldPositionsIterator itr = dst.getIterator();
        EXPECT_EQUAL(itr.getPosition(), 7u);
        itr.next();
        EXPECT_EQUAL(itr.getPosition(), 17u);
        itr.next();
        ASSERT_TRUE(!itr.valid());
    }
}

void
PhraseSplitterTest::testSplitter()
{
    { // single term
        test::QueryEnvironment qe;
        std::vector<SimpleTermData> &terms = qe.getTerms();
        MatchDataLayout mdl;
        terms.push_back(SimpleTermData());
        terms.back().addField(0).setHandle(mdl.allocTermField(0));
        MatchData::UP md = mdl.createMatchData();
        PhraseSplitterQueryEnv ps_query_env(qe, 0);
        PhraseSplitter ps(ps_query_env);
        ASSERT_TRUE(ps.get_query_env().getNumTerms() == 1);
        ps.bind_match_data(*md);
        ps.update();
        // check that nothing is served from the splitter
        EXPECT_EQUAL(ps.get_query_env().getTerm(0), &terms[0]);
        TermFieldHandle handle = terms[0].lookupField(0)->getHandle();
        EXPECT_EQUAL(ps.resolveTermField(handle), md->resolveTermField(handle));
    }
    { // single phrase
        test::QueryEnvironment qe;
        std::vector<SimpleTermData> & terms = qe.getTerms();
        MatchDataLayout mdl;
        terms.push_back(SimpleTermData());
        terms.back().setUniqueId(1);
        terms.back().setPhraseLength(3);
        terms.back().addField(0).setHandle(mdl.allocTermField(0));
        terms.back().addField(7).setHandle(mdl.allocTermField(7));
        MatchData::UP md = mdl.createMatchData();
        PhraseSplitterQueryEnv ps_query_env(qe, 7);
        PhraseSplitter ps(ps_query_env);
        ASSERT_TRUE(ps.get_query_env().getNumTerms() == 3);
        ps.bind_match_data(*md);
        ps.update();
        // check that all is served from the splitter
        for (size_t i = 0; i < 3; ++i) {
            // fprintf(stderr, "checking term %d\n", (int)i);
            const ITermData *td = ps.get_query_env().getTerm(i);
            EXPECT_NOT_EQUAL(td, &terms[0]);
            EXPECT_NOT_EQUAL(td->lookupField(7), (ITermFieldData *)0);
            EXPECT_EQUAL(td->lookupField(0), (ITermFieldData *)0);
            TEST_DO(assertTermData(td, 1, 1, 7, i + 4)); // skipHandles = 4
            EXPECT_NOT_EQUAL(td->lookupField(7)->getHandle(),
                             terms[0].lookupField(7)->getHandle());
            EXPECT_NOT_EQUAL(ps.resolveTermField(td->lookupField(7)->getHandle()),
                             md->resolveTermField(terms[0].lookupField(7)->getHandle()));
        }
    }
    { // combination
        test::QueryEnvironment qe;
        std::vector<SimpleTermData> &terms = qe.getTerms();
        MatchDataLayout mdl;
        for (size_t i = 0; i < 3; ++i) {
            terms.push_back(SimpleTermData());
            terms.back().setUniqueId(i);
            terms.back().setPhraseLength(1);
            terms.back().addField(4).setHandle(mdl.allocTermField(4));
            terms.back().addField(7).setHandle(mdl.allocTermField(7));
            // fprintf(stderr, "setup B term %p #f %zd\n", &terms.back(), terms.back().numFields());
        }
        terms[1].setPhraseLength(3);
        MatchData::UP md = mdl.createMatchData();
        PhraseSplitterQueryEnv ps_query_env(qe, 4);
        PhraseSplitter ps(ps_query_env);
        ASSERT_TRUE(ps.get_query_env().getNumTerms() == 5);
        ps.bind_match_data(*md);
        ps.update();
        { // first term
            // fprintf(stderr, "first term\n");
            EXPECT_EQUAL(ps.get_query_env().getTerm(0), &terms[0]);
            TEST_DO(assertTermData(ps.get_query_env().getTerm(0), 0, 1, 4, 0));
            TEST_DO(assertTermData(ps.get_query_env().getTerm(0), 0, 1, 7, 1));

            TermFieldHandle handle = terms[0].lookupField(4)->getHandle();
            EXPECT_EQUAL(ps.resolveTermField(handle), md->resolveTermField(handle));
            handle = terms[0].lookupField(7)->getHandle();
            EXPECT_EQUAL(ps.resolveTermField(handle), md->resolveTermField(handle));
        }
        for (size_t i = 0; i < 3; ++i) { // phrase
            // fprintf(stderr, "phrase term %zd\n", i);
            const ITermData *td = ps.get_query_env().getTerm(i + 1);
            EXPECT_NOT_EQUAL(td, &terms[1]);
            TEST_DO(assertTermData(td, 1, 1, 4, i + 11)); // skipHandles == 11
            EXPECT_EQUAL(td->lookupField(7),  (ITermFieldData *)0);
            EXPECT_NOT_EQUAL(ps.resolveTermField(td->lookupField(4)->getHandle()),
                           md->resolveTermField(terms[1].lookupField(4)->getHandle()));
        }
        { // last term
            // fprintf(stderr, "last term\n");
            EXPECT_EQUAL(ps.get_query_env().getTerm(4), &terms[2]);
            TEST_DO(assertTermData(ps.get_query_env().getTerm(4), 2, 1, 4, 4));
            TEST_DO(assertTermData(ps.get_query_env().getTerm(4), 2, 1, 7, 5));

            // fprintf(stderr, "inspect term %p #f %zd\n", &terms[2], terms[2].numFields());
            fflush(stderr);
            TermFieldHandle handle = terms[2].lookupField(4)->getHandle();
            EXPECT_EQUAL(ps.resolveTermField(handle), md->resolveTermField(handle));
        }
    }
}

void
PhraseSplitterTest::testSplitterUpdate()
{
    {
        test::QueryEnvironment qe;
        std::vector<SimpleTermData> &terms = qe.getTerms();
        MatchDataLayout mdl;
        for (size_t i = 0; i < 3; ++i) {
            terms.push_back(SimpleTermData());
            terms.back().setUniqueId(i);
            terms.back().setPhraseLength(1);
            terms.back().addField(0).setHandle(mdl.allocTermField(0));
        }
        terms[0].setPhraseLength(2);
        terms[2].setPhraseLength(2);
        MatchData::UP md = mdl.createMatchData();
        PhraseSplitterQueryEnv ps_query_env(qe, 0);
        PhraseSplitter ps(ps_query_env);
        ASSERT_TRUE(ps.get_query_env().getNumTerms() == 5);
        { // first phrase
            TermFieldMatchData * tmd = md->resolveTermField(terms[0].lookupField(0)->getHandle());
            tmd->appendPosition(TermFieldMatchDataPosition(0, 10, 0, 1000));
        }
        { // first term
            TermFieldMatchData * tmd = md->resolveTermField(terms[1].lookupField(0)->getHandle());
            tmd->appendPosition(TermFieldMatchDataPosition(0, 20, 0, 1000));
        }
        { // second phrase
            TermFieldMatchData * tmd = md->resolveTermField(terms[2].lookupField(0)->getHandle());
            tmd->appendPosition(TermFieldMatchDataPosition(0, 30, 0, 1000));
        }
        ps.bind_match_data(*md);
        ps.update();
        for (size_t i = 0; i < 2; ++i) { // first phrase
            const TermFieldMatchData * tmd = ps.resolveTermField(ps.get_query_env().getTerm(i)->lookupField(0)->getHandle());
            TermFieldMatchData::PositionsIterator itr = tmd->begin();
            EXPECT_EQUAL((itr++)->getPosition(), 10 + i);
            ASSERT_TRUE(itr == tmd->end());
        }
        { // first term
            TermFieldMatchData * tmd = md->resolveTermField(ps.get_query_env().getTerm(2)->lookupField(0)->getHandle());
            TermFieldMatchData::PositionsIterator itr = tmd->begin();
            EXPECT_EQUAL((itr++)->getPosition(), 20u);
            ASSERT_TRUE(itr == tmd->end());
        }
        for (size_t i = 0; i < 2; ++i) { // second phrase
            const TermFieldMatchData * tmd = ps.resolveTermField(ps.get_query_env().getTerm(i + 3)->lookupField(0)->getHandle());
            TermFieldMatchData::PositionsIterator itr = tmd->begin();
            EXPECT_EQUAL((itr++)->getPosition(), 30 + i);
            ASSERT_TRUE(itr == tmd->end());
        }
    }
}

int
PhraseSplitterTest::Main()
{

    TEST_INIT("phrasesplitter_test");

    testCopyTermFieldMatchData();
    testSplitter();
    testSplitterUpdate();

    TEST_DONE();
}

}
}

TEST_APPHOOK(search::fef::PhraseSplitterTest);
