// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/util/rand48.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/bitcompression/countcompression.h>
#include <vespa/searchlib/bitcompression/pagedict4.h>
#include <vespa/searchlib/test/diskindex/threelevelcountbuffers.h>
#include <vespa/searchlib/test/diskindex/pagedict4_mem_writer.h>
#include <vespa/searchlib/test/diskindex/pagedict4_mem_seq_reader.h>
#include <vespa/searchlib/test/diskindex/pagedict4_mem_rand_reader.h>
#include <vespa/searchlib/index/postinglistcounts.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/diskindex/pagedict4file.h>
#include <vespa/searchlib/diskindex/pagedict4randread.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/fastos/app.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP("pagedict4test");

using search::bitcompression::PageDict4PLookupRes;
using search::bitcompression::PageDict4PWriter;
using search::bitcompression::PageDict4Reader;
using search::bitcompression::PageDict4SPLookupRes;
using search::bitcompression::PageDict4SPWriter;
using search::bitcompression::PageDict4SSLookupRes;
using search::bitcompression::PageDict4SSReader;
using search::bitcompression::PageDict4SSWriter;
using search::bitcompression::PostingListCountFileDecodeContext;
using search::bitcompression::PostingListCountFileEncodeContext;
using search::diskindex::PageDict4FileSeqRead;
using search::diskindex::PageDict4FileSeqWrite;
using search::diskindex::PageDict4RandRead;
using search::index::DictionaryFileRandRead;
using search::index::DictionaryFileSeqRead;
using search::index::DictionaryFileSeqWrite;
using search::index::DummyFileHeaderContext;
using search::index::PostingListCounts;
using search::index::PostingListOffsetAndCounts;
using search::index::PostingListParams;
using search::index::Schema;
using search::index::schema::CollectionType;
using search::index::schema::DataType;

using namespace search::index;

using StartOffset = search::bitcompression::PageDict4StartOffset;
using Writer = search::diskindex::test::PageDict4MemWriter;
using SeqReader = search::diskindex::test::PageDict4MemSeqReader;
using RandReader = search::diskindex::test::PageDict4MemRandReader;

class PageDict4TestApp : public FastOS_Application
{
public:
    search::Rand48 _rnd;
    bool _stress;
    bool _emptyWord;
    bool _firstWordForcedCommon;
    bool _lastWordForcedCommon;

    void usage();
    int Main() override;
    void testWords();
    PageDict4TestApp()
        : _rnd(),
          _stress(false),
          _emptyWord(false),
          _firstWordForcedCommon(false),
          _lastWordForcedCommon(false)
    {
    }
};


void
PageDict4TestApp::usage()
{
    printf("Usage: wordnumbers\n");
    fflush(stdout);
}


int
PageDict4TestApp::Main()
{
    if (_argc > 0) {
        DummyFileHeaderContext::setCreator(_argv[0]);
    }
    _rnd.srand48(32);
    for (int32_t i = 1; i < _argc; ++i) {
        if (strcmp(_argv[i], "stress") == 0)
            _stress = true;
        if (strcmp(_argv[i], "emptyword") == 0)
            _emptyWord = true;
        if (strcmp(_argv[i], "firstwordforcedcommon") == 0)
            _firstWordForcedCommon = true;
        if (strcmp(_argv[i], "lastwordforcedcommon") == 0)
            _lastWordForcedCommon = true;
    }
    testWords();

    LOG(info,
        "_stress is %s",
        _stress ? "true" : "false");
    LOG(info,
        "_emptyWord is %s",
        _emptyWord ? "true" : "false");
    LOG(info,
        "_firstWordForcedCommon is %s",
        _firstWordForcedCommon ? "true" : "false");
    LOG(info,
        "_lastWordForcedCommon is %s",
        _lastWordForcedCommon ? "true" : "false");

    LOG(info, "SUCCESS");
    return 0;
}


class WordIndexCounts
{
public:
    uint32_t _numDocs;
    uint64_t _fileOffset;
    uint64_t _bitLength;
    uint64_t _accNumDocs;

    WordIndexCounts(uint64_t bitLength,
              uint32_t numDocs)
        : _numDocs(numDocs),
          _fileOffset(0),
          _bitLength(bitLength),
          _accNumDocs(0)
    {
    }

    WordIndexCounts()
        : _numDocs(0),
          _fileOffset(0),
          _bitLength(0),
          _accNumDocs(0)
    {
    }
};

class WordCounts
{
public:
    std::string _word;
    WordIndexCounts _counts;

    bool
    operator!=(const WordCounts &rhs) const
    {
        return _word != rhs._word;
    }

    WordCounts(const std::string &word)
        : _word(word),
          _counts()
    {
    }

    bool
    operator<(const WordCounts &rhs) const
    {
        return _word < rhs._word;
    }
};


void
deDup(std::vector<WordCounts> &v)
{
    std::vector<WordCounts> v2;
    std::sort(v.begin(), v.end());
    for (std::vector<WordCounts>::const_iterator
             i = v.begin(),
             ie = v.end();
         i != ie;
         ++i) {
        if (v2.empty() || v2.back() != *i)
            v2.push_back(*i);
    }
    std::swap(v, v2);
}


void
deDup(std::vector<uint32_t> &v)
{
    std::vector<uint32_t> v2;
    std::sort(v.begin(), v.end());
    for (std::vector<uint32_t>::const_iterator
             i = v.begin(),
             ie = v.end();
         i != ie;
         ++i) {
        if (v2.empty() || v2.back() != *i)
            v2.push_back(*i);
    }
    std::swap(v, v2);
}


static WordIndexCounts
makeIndex(search::Rand48 &rnd, bool forceCommon)
{
    uint64_t bitLength = 10;
    uint32_t numDocs = 1;
    if ((rnd.lrand48() % 150) == 0 || forceCommon) {
        bitLength = 1000000000;
        numDocs = 500000;
    }
    return WordIndexCounts(bitLength, numDocs);
}


void
makeIndexes(search::Rand48 &rnd,
            WordIndexCounts &counts,
            bool forceCommon)
{
    counts = makeIndex(rnd, forceCommon);
}


static void
makeWords(std::vector<WordCounts> &v,
          search::Rand48 &rnd,
          uint32_t numWordIds,
          uint32_t tupleCount,
          bool emptyWord,
          bool firstWordForcedCommon,
          bool lastWordForcedCommon)
{
    v.clear();
    for (unsigned int i = 0; i < tupleCount; ++i) {
        uint64_t word = rnd.lrand48() % numWordIds;
        uint64_t wordCount = (rnd.lrand48() % 10) + 1;
        for (unsigned int j = 0; j < wordCount; ++j) {
            uint64_t nextWord = rnd.lrand48() % numWordIds;
            uint64_t nextWordCount = 0;
            bool incomplete = true;
            nextWordCount = rnd.lrand48() % 10;
            incomplete = (rnd.lrand48() % 3) == 0 || nextWordCount == 0;
            for (unsigned int k = 0; k < nextWordCount; ++k) {
                uint64_t nextNextWord = rnd.lrand48() % numWordIds;
                std::ostringstream w;
                w << word;
                w << "-";
                w << nextWord;
                w << "-";
                w << nextNextWord;
                v.push_back(WordCounts(w.str()));
            }
            if (incomplete) {
                std::ostringstream w;
                w << word;
                w << "-";
                w << nextWord;
                w << "-";
                w << "9999999999999999";
                v.push_back(WordCounts(w.str()));
            }
        }
    }
    deDup(v);
    if (!v.empty() && emptyWord)
        v.front()._word = "";
    for (std::vector<WordCounts>::iterator
             i = v.begin(), ib = v.begin(), ie = v.end();
         i != ie; ++i) {
        std::vector<WordIndexCounts> indexes;
        makeIndexes(rnd, i->_counts,
                    (i == ib && firstWordForcedCommon) ||
                    (i + 1 == ie && lastWordForcedCommon));
    }
    uint64_t fileOffset = 0;
    uint64_t accNumDocs = 0;
    for (std::vector<WordCounts>::iterator
             i = v.begin(),
             ie = v.end();
         i != ie;
         ++i) {
        WordIndexCounts *f = &i->_counts;
        assert(f->_numDocs > 0);
        assert(f->_bitLength > 0);
        f->_fileOffset = fileOffset;
        f->_accNumDocs = accNumDocs;
        fileOffset += f->_bitLength;
        accNumDocs += f->_numDocs;
    }
}


void
makeCounts(PostingListCounts &counts,
           const WordCounts &i,
           uint32_t chunkSize)
{
    PostingListCounts c;
    const WordIndexCounts *j = &i._counts;
    c._bitLength = j->_bitLength;
    c._numDocs = j->_numDocs;
    c._segments.clear();
    assert(j->_numDocs > 0);
    uint32_t numChunks = (j->_numDocs + chunkSize - 1) / chunkSize;
    if (numChunks > 1) {
        uint32_t chunkBits = j->_bitLength / numChunks;
        for (uint32_t chunkNo = 0; chunkNo < numChunks; ++chunkNo) {
            PostingListCounts::Segment seg;
            seg._bitLength = chunkBits;
            seg._numDocs = chunkSize;
            seg._lastDoc = (chunkNo + 1) * chunkSize - 1;
            if (chunkNo + 1 == numChunks) {
                seg._bitLength = c._bitLength -
                                 (numChunks - 1) * chunkBits;
                seg._lastDoc = c._numDocs - 1;
                seg._numDocs = c._numDocs - (numChunks - 1) * chunkSize;
            }
            c._segments.push_back(seg);
        }
    }
    counts = c;
}


void
checkCounts(const std::string &word,
            const PostingListCounts &counts,
            const StartOffset &fileOffset,
            const WordCounts &i,
            uint32_t chunkSize)
{
    PostingListCounts answer;

    makeCounts(answer, i, chunkSize);
    assert(word == i._word);
    (void) word;
    (void) fileOffset;
    const WordIndexCounts *j = &i._counts;
    (void) j;
    assert(counts._bitLength == j->_bitLength);
    assert(counts._numDocs == j->_numDocs);
    assert(fileOffset._fileOffset == j->_fileOffset);
    assert(fileOffset._accNumDocs == j->_accNumDocs);
    assert(counts._segments == answer._segments);
    assert(counts == answer);
    (void) counts;
}


void
testWords(const std::string &logname,
          search::Rand48 &rnd,
          uint64_t numWordIds,
          uint32_t tupleCount,
          uint32_t chunkSize,
          uint32_t ssPad,
          uint32_t spPad,
          uint32_t pPad,
          bool emptyWord,
          bool firstWordForcedCommon,
          bool lastWordForcedCommon)
{
    typedef search::bitcompression::PostingListCountFileEncodeContext EC;
    typedef search::bitcompression::PostingListCountFileDecodeContext DC;

    LOG(info, "%s: word test start", logname.c_str());
    std::vector<WordCounts> myrand;
    makeWords(myrand, rnd, numWordIds, tupleCount,
              emptyWord, firstWordForcedCommon, lastWordForcedCommon);

    PostingListCounts xcounts;
    for (std::vector<WordCounts>::const_iterator
             i = myrand.begin(),
             ie = myrand.end();
         i != ie;
         ++i) {
        makeCounts(xcounts, *i, chunkSize);
    }
    LOG(info, "%s: word counts generated", logname.c_str());

    EC pe;
    EC spe;
    EC sse;

    sse._minChunkDocs = chunkSize;
    sse._numWordIds = numWordIds;
    spe.copyParams(sse);
    pe.copyParams(sse);
    Writer w(sse, spe, pe);
    w.startPad(ssPad, spPad, pPad);
    w.allocWriters();

    PostingListCounts counts;
    for (std::vector<WordCounts>::const_iterator
             i = myrand.begin(),
             ie = myrand.end();
         i != ie;
         ++i) {
        makeCounts(counts, *i, chunkSize);
        w.addCounts(i->_word, counts);
    }
    w.flush();

    LOG(info,
        "%s: Used %" PRIu64 "+%" PRIu64 "+%" PRIu64
        " bits for %d words",
        logname.c_str(),
        w._pFileBitSize,
        w._spFileBitSize,
        w._ssFileBitSize,
        (int) myrand.size());

    StartOffset checkOffset;

    {
        DC ssd;
        ssd._minChunkDocs = chunkSize;
        ssd._numWordIds = numWordIds;
        DC spd;
        spd.copyParams(ssd);
        DC pd;
        pd.copyParams(ssd);

        SeqReader r(ssd, spd, pd, w);

        uint64_t wordNum = 1;
        uint64_t checkWordNum = 0;
        for (std::vector<WordCounts>::const_iterator
                 i = myrand.begin(),
                 ie = myrand.end();
             i != ie;
             ++i, ++wordNum) {
            vespalib::string word;
            counts.clear();
            r.readCounts(word, checkWordNum, counts);
            checkCounts(word, counts, checkOffset, *i, chunkSize);
            assert(checkWordNum == wordNum);
            checkOffset._fileOffset += counts._bitLength;
            checkOffset._accNumDocs += counts._numDocs;
        }
        assert(pd.getReadOffset() == w._pFileBitSize);
        LOG(info, "%s: words seqRead test OK", logname.c_str());
    }

    {
        DC ssd;
        ssd._minChunkDocs = chunkSize;
        ssd._numWordIds = numWordIds;
        DC spd;
        spd.copyParams(ssd);
        DC pd;
        pd.copyParams(ssd);

        RandReader rr(ssd, spd, pd, w);

        uint64_t wordNum = 1;
        uint64_t checkWordNum = 0;
        for (std::vector<WordCounts>::const_iterator
                 i = myrand.begin(),
                 ie = myrand.end();
             i != ie;
             ++i, ++wordNum) {
            checkWordNum = 0;
            bool res = rr.lookup(i->_word,
                                 checkWordNum,
                                 counts,
                                 checkOffset);
            assert(res);
            (void) res;
            checkCounts(i->_word, counts, checkOffset,
                        *i, chunkSize);
            assert(checkWordNum == wordNum);
        }
        LOG(info, "%s: word randRead test OK", logname.c_str());
    }

    Schema schema;
    std::vector<uint32_t> indexes;
    {
        std::ostringstream fn;
        fn << "f0";
        schema.addIndexField(Schema::
                             IndexField(fn.str(),
                                        DataType::STRING,
                                        CollectionType::SINGLE));
        indexes.push_back(0);
    }
    {
        std::unique_ptr<DictionaryFileSeqWrite>
            dw(new PageDict4FileSeqWrite);
        std::vector<uint32_t> wIndexes;
        std::vector<PostingListCounts> wCounts;
        search::TuneFileSeqWrite tuneFileWrite;
        DummyFileHeaderContext fileHeaderContext;
        PostingListParams params;
        params.set("numWordIds", numWordIds);
        params.set("minChunkDocs", chunkSize);
        dw->setParams(params);
        bool openres = dw->open("fakedict",
                                tuneFileWrite,
                                fileHeaderContext);
        assert(openres);
        (void) openres;

        for (std::vector<WordCounts>::const_iterator
                 i = myrand.begin(),
                 ie = myrand.end();
             i != ie;
             ++i) {
            makeCounts(counts, *i, chunkSize);
            dw->writeWord(i->_word, counts);
        }
        bool closeres = dw->close();
        assert(closeres);
        (void) closeres;

        LOG(info, "%s: pagedict4 written", logname.c_str());
    }
    {
        std::unique_ptr<DictionaryFileSeqRead> dr(new PageDict4FileSeqRead);
        search::TuneFileSeqRead tuneFileRead;

        bool openres = dr->open("fakedict",
                                tuneFileRead);
        assert(openres);
        (void) openres;
        std::string lastWord;
        vespalib::string checkWord;
        PostingListCounts wCounts;
        PostingListCounts rCounts;
        uint64_t wordNum = 1;
        uint64_t checkWordNum = 5;
        for (std::vector<WordCounts>::const_iterator
                 i = myrand.begin(),
                 ie = myrand.end();
             i != ie;
             ++i, ++wordNum) {
            makeCounts(counts, *i, chunkSize);
            wCounts = counts;
            checkWord.clear();
            checkWordNum = 0;
            dr->readWord(checkWord, checkWordNum, rCounts);
            assert(rCounts == wCounts);
            assert(wordNum == checkWordNum);
            assert(checkWord == i->_word);
        }

        checkWord = "bad";
        checkWordNum = 5;
        dr->readWord(checkWord, checkWordNum, rCounts);
        assert(checkWord.empty());
        assert(checkWordNum == DictionaryFileSeqRead::noWordNumHigh());
        bool closeres = dr->close();
        assert(closeres);
        (void) closeres;

        LOG(info, "%s: pagedict4 seqverify OK", logname.c_str());
    }
    {
        std::unique_ptr<DictionaryFileRandRead> drr(new PageDict4RandRead);
        search::TuneFileRandRead tuneFileRead;
        bool openres = drr->open("fakedict",
                                 tuneFileRead);
        assert(openres);
        (void) openres;
        std::string lastWord;
        vespalib::string checkWord;
        PostingListCounts wCounts;
        PostingListCounts rCounts;
        uint64_t wOffset;
        uint64_t rOffset;
        (void) rOffset;
        PostingListOffsetAndCounts rOffsetAndCounts;
        uint64_t wordNum = 1;
        uint64_t checkWordNum = 5;
        std::string missWord;
        wOffset = 0;
        for (std::vector<WordCounts>::const_iterator
                 i = myrand.begin(),
                 ie = myrand.end();
             i != ie;
             ++i, ++wordNum) {
            makeCounts(counts, *i, chunkSize);
            wCounts = counts;

            checkWordNum = 0;
            rCounts.clear();
            rOffset = 0;
            bool lres = drr->lookup(i->_word, checkWordNum,
                                    rOffsetAndCounts);
            assert(lres);
            (void) lres;
            assert((rOffsetAndCounts._counts._bitLength == 0) ==
                   (rOffsetAndCounts._counts._numDocs == 0));
            rOffset = rOffsetAndCounts._offset;
            rCounts = rOffsetAndCounts._counts;
            assert(rCounts == wCounts);
            assert(wordNum == checkWordNum);
            assert(rOffset == wOffset);

            wOffset += wCounts._bitLength;
            lastWord = i->_word;

            missWord = i->_word;
            missWord.append(1, '\1');
            checkWordNum = 0;
            lres = drr->lookup(missWord, checkWordNum,
                               rOffsetAndCounts);
            assert(!lres);
            assert(checkWordNum == wordNum + 1);
        }

        checkWordNum = 0;
        std::string notfoundword = "Thiswordhasbetternotbeindictionary";
        bool lres = drr->lookup(notfoundword, checkWordNum,
                                rOffsetAndCounts);
        assert(!lres);
        checkWordNum = 0;
        notfoundword = lastWord + "somethingmore";
        lres = drr->lookup(notfoundword, checkWordNum,
                           rOffsetAndCounts);
        assert(!lres);
        (void) lres;
        LOG(info, "Lookup beyond dict EOF gave wordnum %d", (int) checkWordNum);

        if (firstWordForcedCommon) {
            if (!emptyWord) {
                checkWordNum = 0;
                notfoundword = "";
                lres = drr->lookup(notfoundword, checkWordNum,
                                        rOffsetAndCounts);
                assert(!lres);
                assert(checkWordNum == 1);
            }
            if (!myrand.empty()) {
                checkWordNum = 0;
                notfoundword = myrand.front()._word;
                notfoundword.append(1, '\1');
                lres = drr->lookup(notfoundword, checkWordNum,
                                   rOffsetAndCounts);
                assert(!lres);
                assert(checkWordNum == 2);
            }
        }
        if (lastWordForcedCommon && !myrand.empty()) {
            if (myrand.size() > 1) {
                checkWordNum = 0;
                notfoundword = myrand[myrand.size() - 2]._word;
                notfoundword.append(1, '\1');
                lres = drr->lookup(notfoundword, checkWordNum,
                                        rOffsetAndCounts);
                assert(!lres);
                assert(checkWordNum == myrand.size());
            }
            checkWordNum = 0;
            notfoundword = myrand[myrand.size() - 1]._word;
            notfoundword.append(1, '\1');
            lres = drr->lookup(notfoundword, checkWordNum,
                               rOffsetAndCounts);
            assert(!lres);
            assert(checkWordNum == myrand.size() + 1);
        }
        bool closeres = drr->close();
        assert(closeres);
        (void) closeres;
        LOG(info, "%s: pagedict4 randverify OK", logname.c_str());
    }
}


void
PageDict4TestApp::testWords()
{
    ::testWords("smallchunkwordsempty", _rnd,
                1000000, 0,
                64, 80, 72, 64,
                false, false, false);
    ::testWords("smallchunkwordsempty2", _rnd,
                0, 0,
                64, 80, 72, 64,
                false, false, false);
    ::testWords("smallchunkwords", _rnd,
                1000000, 100,
                64, 80, 72, 64,
                false, false, false);
    ::testWords("smallchunkwordswithemptyword", _rnd,
                1000000, 100,
                64, 80, 72, 64,
                true, false, false);
    ::testWords("smallchunkwordswithcommonfirstword", _rnd,
                1000000, 100,
                64, 80, 72, 64,
                false, true, false);
    ::testWords("smallchunkwordswithcommonemptyfirstword", _rnd,
                1000000, 100,
                64, 80, 72, 64,
                true, true, false);
    ::testWords("smallchunkwordswithcommonlastword", _rnd,
                1000000, 100,
                64, 80, 72, 64,
                false, false, true);
#if 1
    ::testWords("smallchunkwords2", _rnd,
                1000000, _stress ? 10000 : 100,
                64, 80, 72, 64,
                _emptyWord, _firstWordForcedCommon, _lastWordForcedCommon);
#endif
#if 1
    ::testWords("stdwords", _rnd,
                1000000, _stress ? 10000 : 100,
                262144, 80, 72, 64,
                _emptyWord, _firstWordForcedCommon, _lastWordForcedCommon);
#endif
}

FASTOS_MAIN(PageDict4TestApp);
