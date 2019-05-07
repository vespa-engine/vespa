// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/searchlib/util/rand48.h>
#include <vespa/searchlib/test/fakedata/fakeword.h>
#include <vespa/searchlib/test/fakedata/fakewordset.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/postinglisthandle.h>
#include <vespa/searchlib/diskindex/zcposocc.h>
#include <vespa/searchlib/diskindex/zcposoccrandread.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/diskindex/fieldwriter.h>
#include <vespa/searchlib/diskindex/fieldreader.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/searchlib/diskindex/pagedict4file.h>
#include <vespa/searchlib/diskindex/pagedict4randread.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/fastos/time.h>
#include <vespa/fastos/app.h>
#include <vespa/log/log.h>
LOG_SETUP("fieldwriter_test");

using search::ResultSet;
using search::TuneFileRandRead;
using search::TuneFileSeqRead;
using search::TuneFileSeqWrite;
using search::common::FileHeaderContext;
using search::diskindex::DocIdMapping;
using search::diskindex::FieldReader;
using search::diskindex::FieldWriter;
using search::diskindex::PageDict4RandRead;
using search::diskindex::WordNumMapping;
using search::fakedata::FakeWord;
using search::fakedata::FakeWordSet;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::index::DummyFileHeaderContext;
using search::index::PostingListCounts;
using search::index::PostingListOffsetAndCounts;
using search::index::PostingListParams;
using search::index::Schema;
using search::index::SchemaUtil;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using search::queryeval::SearchIterator;

using namespace search::index;

// needed to resolve external symbol from httpd.h on AIX
void FastS_block_usr2() { }

namespace fieldwriter {

uint32_t minSkipDocs = 64;
uint32_t minChunkDocs = 262144;

vespalib::string dirprefix = "index/";

void disableSkip()
{
    minSkipDocs = 10000000;
    minChunkDocs = 1 << 30;
}

void enableSkip()
{
    minSkipDocs = 64;
    minChunkDocs = 1 << 30;
}

void enableSkipChunks()
{
    minSkipDocs = 64;
    minChunkDocs = 9000;    // Unrealistic low for testing
}


vespalib::string
makeWordString(uint64_t wordNum)
{
    using AS = vespalib::asciistream;
    AS ws;
    ws << AS::Width(4) << AS::Fill('0') << wordNum;
    return ws.str();
}


class FieldWriterTest : public FastOS_Application
{
private:
    bool _verbose;
    uint32_t _numDocs;
    uint32_t _commonDocFreq;
    uint32_t _numWordsPerClass;
    FakeWordSet _wordSet;
    FakeWordSet _wordSet2;
public:
    search::Rand48 _rnd;

private:
    void Usage();
    void testFake(const std::string &postingType, FakeWord &fw);
public:
    FieldWriterTest();
    ~FieldWriterTest();
    int Main() override;
};


void
FieldWriterTest::Usage()
{
    printf("fieldwriter_test "
           "[-c <commonDocFreq>] "
           "[-d <numDocs>] "
           "[-v] "
           "[-w <numWordPerClass>]\n");
}


FieldWriterTest::FieldWriterTest()
    : _verbose(false),
      _numDocs(3000000),
      _commonDocFreq(50000),
      _numWordsPerClass(6),
      _wordSet(),
      _wordSet2(),
      _rnd()
{
}


FieldWriterTest::~FieldWriterTest()
{
}


class WrappedFieldWriter
{
public:
    std::unique_ptr<FieldWriter> _fieldWriter;
private:
    bool _dynamicK;
    uint32_t _numWordIds;
    uint32_t _docIdLimit;
    vespalib::string _namepref;
    Schema _schema;
    uint32_t _indexId;

public:

    WrappedFieldWriter(const vespalib::string &namepref,
                      bool dynamicK,
                      uint32_t numWordIds,
                      uint32_t docIdLimit);
    ~WrappedFieldWriter();

    void open();
    void close();
};

WrappedFieldWriter::~WrappedFieldWriter() {}

WrappedFieldWriter::WrappedFieldWriter(const vespalib::string &namepref,
                                       bool dynamicK,
                                       uint32_t numWordIds,
                                       uint32_t docIdLimit)
    : _fieldWriter(),
      _dynamicK(dynamicK),
      _numWordIds(numWordIds),
      _docIdLimit(docIdLimit),
      _namepref(dirprefix + namepref),
      _schema(),
      _indexId()
{
    schema::CollectionType ct(CollectionType::SINGLE);
    _schema.addIndexField(Schema::IndexField("field1", DataType::STRING, ct));
    _indexId = _schema.getIndexFieldId("field1");
}


void
WrappedFieldWriter::open()
{
    TuneFileSeqWrite tuneFileWrite;
    DummyFileHeaderContext fileHeaderContext;
    fileHeaderContext.disableFileName();
    _fieldWriter = std::make_unique<FieldWriter>(_docIdLimit, _numWordIds);
    _fieldWriter->open(_namepref,
                       minSkipDocs, minChunkDocs, _dynamicK, _schema,
                       _indexId,
                       tuneFileWrite, fileHeaderContext);
}


void
WrappedFieldWriter::close()
{
    _fieldWriter->close();
    _fieldWriter.reset();
}


class WrappedFieldReader
{
public:
    std::unique_ptr<FieldReader> _fieldReader;
private:
    std::string _namepref;
    uint32_t _numWordIds;
    uint32_t _docIdLimit;
    WordNumMapping _wmap;
    DocIdMapping _dmap;
    Schema _oldSchema;
    Schema _schema;

public:
    WrappedFieldReader(const vespalib::string &namepref,
                      uint32_t numWordIds,
                      uint32_t docIdLimit);

    ~WrappedFieldReader();
    void open();
    void close();
};


WrappedFieldReader::WrappedFieldReader(const vespalib::string &namepref,
                                     uint32_t numWordIds,
                                     uint32_t docIdLimit)
    : _fieldReader(),
      _namepref(dirprefix + namepref),
      _numWordIds(numWordIds),
      _docIdLimit(docIdLimit),
      _wmap(),
      _dmap(),
      _oldSchema(),
      _schema()
{
    Schema::CollectionType ct(CollectionType::SINGLE);
    _oldSchema.addIndexField(Schema::IndexField("field1",
                                                DataType::STRING,
                                                ct));
    _schema.addIndexField(Schema::IndexField("field1",
                                             DataType::STRING,
                                             ct));
}


WrappedFieldReader::~WrappedFieldReader()
{
}

void
WrappedFieldReader::open()
{
    TuneFileSeqRead tuneFileRead;
    _wmap.setup(_numWordIds);
    _dmap.setup(_docIdLimit);
    _fieldReader = std::make_unique<FieldReader>();
    _fieldReader->setup(_wmap, _dmap);
    _fieldReader->open(_namepref, tuneFileRead);
}

void
WrappedFieldReader::close()
{
    _fieldReader->close();
    _fieldReader.reset();
}


void
writeField(FakeWordSet &wordSet,
           uint32_t docIdLimit,
           const std::string &namepref,
           bool dynamicK)
{
    const char *dynamicKStr = dynamicK ? "true" : "false";

    FastOS_Time tv;
    double before;
    double after;

    LOG(info,
        "enter writeField, "
        "namepref=%s, dynamicK=%s",
        namepref.c_str(),
        dynamicKStr);
    tv.SetNow();
    before = tv.Secs();
    WrappedFieldWriter ostate(namepref,
                             dynamicK,
                             wordSet.getNumWords(), docIdLimit);
    FieldWriter::remove(namepref);
    ostate.open();

    unsigned int wordNum = 1;
    for (const auto& words : wordSet.words()) {
        for (const auto& word : words) {
            ostate._fieldWriter->newWord(makeWordString(wordNum));
            word->dump(*ostate._fieldWriter, false);
            ++wordNum;
        }
    }
    ostate.close();

    tv.SetNow();
    after = tv.Secs();
    LOG(info,
        "leave writeField, "
        "namepref=%s, dynamicK=%s"
        " elapsed=%10.6f",
        namepref.c_str(),
        dynamicKStr,
        after - before);
}


void
readField(FakeWordSet &wordSet,
          uint32_t docIdLimit,
          const std::string &namepref,
          bool dynamicK,
          bool verbose)
{
    const char *dynamicKStr = dynamicK ? "true" : "false";

    FastOS_Time tv;
    double before;
    double after;
    WrappedFieldReader istate(namepref, wordSet.getNumWords(),
                             docIdLimit);
    LOG(info,
        "enter readField, "
        "namepref=%s, dynamicK=%s",
        namepref.c_str(),
        dynamicKStr);
    tv.SetNow();
    before = tv.Secs();
    istate.open();
    if (istate._fieldReader->isValid())
        istate._fieldReader->read();

    TermFieldMatchData mdfield1;

    unsigned int wordNum = 1;
    for (const auto& words : wordSet.words()) {
        for (const auto& word : words) {
            TermFieldMatchDataArray tfmda;
            tfmda.add(&mdfield1);

            word->validate(*istate._fieldReader, wordNum, tfmda, verbose);
            ++wordNum;
        }
    }

    istate.close();
    tv.SetNow();
    after = tv.Secs();
    LOG(info,
        "leave readField, "
        "namepref=%s, dynamicK=%s"
        " elapsed=%10.6f",
        namepref.c_str(),
        dynamicKStr,
        after - before);
}


void
randReadField(FakeWordSet &wordSet,
              const std::string &namepref,
              bool dynamicK,
              bool verbose)
{
    const char *dynamicKStr = dynamicK ? "true" : "false";

    FastOS_Time tv;
    double before;
    double after;
    PostingListCounts counts;

    LOG(info,
        "enter randReadField,"
        " namepref=%s, dynamicK=%s",
        namepref.c_str(),
        dynamicKStr);
    tv.SetNow();
    before = tv.Secs();

    std::string cname = dirprefix + namepref;
    cname += "dictionary";

    std::unique_ptr<search::index::DictionaryFileRandRead> dictFile;
    dictFile.reset(new PageDict4RandRead);

    search::index::PostingListFileRandRead *postingFile = NULL;
    if (dynamicK)
        postingFile =
            new search::diskindex::ZcPosOccRandRead;
    else
        postingFile =
            new search::diskindex::Zc4PosOccRandRead;

    TuneFileSeqRead tuneFileRead;
    TuneFileRandRead tuneFileRandRead;
    bool openCntRes = dictFile->open(cname, tuneFileRandRead);
    assert(openCntRes);
    (void) openCntRes;
    vespalib::string cWord;

    std::string pname = dirprefix + namepref + "posocc.dat";
    pname += ".compressed";
    bool openPostingRes = postingFile->open(pname, tuneFileRandRead);
    assert(openPostingRes);
    (void) openPostingRes;

    for (int loop = 0; loop < 1; ++loop) {
        unsigned int wordNum = 1;
        for (const auto& words : wordSet.words()) {
            for (const auto& word : words) {
                PostingListOffsetAndCounts offsetAndCounts;
                uint64_t checkWordNum;
                dictFile->lookup(makeWordString(wordNum),
                                 checkWordNum,
                                 offsetAndCounts);
                assert(wordNum == checkWordNum);

                counts = offsetAndCounts._counts;
                search::index::PostingListHandle handle;

                handle._bitLength = counts._bitLength;
                handle._file = postingFile;
                handle._bitOffset = offsetAndCounts._offset;

                postingFile->readPostingList(counts,
                        0,
                        counts._segments.empty() ? 1 : counts._segments.size(),
                        handle);

                TermFieldMatchData mdfield1;
                TermFieldMatchDataArray tfmda;
                tfmda.add(&mdfield1);

                std::unique_ptr<SearchIterator>
                    sb(handle.createIterator(counts, tfmda));

                // LOG(info, "loop=%d, wordNum=%u", loop, wordNum);
                word->validate(sb.get(), tfmda, verbose);
                word->validate(sb.get(), tfmda, 19, verbose);
                word->validate(sb.get(), tfmda, 99, verbose);
                word->validate(sb.get(), tfmda, 799, verbose);
                word->validate(sb.get(), tfmda, 6399, verbose);
                word->validate(sb.get(), tfmda, 11999, verbose);
                ++wordNum;
            }
        }
    }

    postingFile->close();
    dictFile->close();
    delete postingFile;
    dictFile.reset();
    tv.SetNow();
    after = tv.Secs();
    LOG(info,
        "leave randReadField, namepref=%s,"
        " dynamicK=%s, "
        "elapsed=%10.6f",
        namepref.c_str(),
        dynamicKStr,
        after - before);
}


void
fusionField(uint32_t numWordIds,
            uint32_t docIdLimit,
            const vespalib::string &ipref,
            const vespalib::string &opref,
            bool doRaw,
            bool dynamicK)
{
    const char *rawStr = doRaw ? "true" : "false";
    const char *dynamicKStr = dynamicK ? "true" : "false";


    LOG(info,
        "enter fusionField, ipref=%s, opref=%s,"
        " raw=%s,"
        " dynamicK=%s",
        ipref.c_str(),
        opref.c_str(),
        rawStr,
        dynamicKStr);

    FastOS_Time tv;
    double before;
    double after;
    WrappedFieldWriter ostate(opref,
                             dynamicK,
                             numWordIds, docIdLimit);
    WrappedFieldReader istate(ipref, numWordIds, docIdLimit);

    tv.SetNow();
    before = tv.Secs();

    ostate.open();
    istate.open();

    if (doRaw) {
        PostingListParams featureParams;
        featureParams.clear();
        featureParams.set("cooked", false);
        istate._fieldReader->setFeatureParams(featureParams);
    }
    if (istate._fieldReader->isValid())
        istate._fieldReader->read();

    while (istate._fieldReader->isValid()) {
        istate._fieldReader->write(*ostate._fieldWriter);
        istate._fieldReader->read();
    }
    istate.close();
    ostate.close();
    tv.SetNow();
    after = tv.Secs();
    LOG(info,
        "leave fusionField, ipref=%s, opref=%s,"
        " raw=%s dynamicK=%s, "
        " elapsed=%10.6f",
        ipref.c_str(),
        opref.c_str(),
        rawStr,
        dynamicKStr,
        after - before);
}


void
testFieldWriterVariants(FakeWordSet &wordSet,
                        uint32_t docIdLimit, bool verbose)
{
    disableSkip();
    writeField(wordSet, docIdLimit, "new4", true);
    readField(wordSet, docIdLimit, "new4", true, verbose);
    readField(wordSet, docIdLimit, "new4", true, verbose);
    writeField(wordSet, docIdLimit, "new5", false);
    readField(wordSet, docIdLimit, "new5", false, verbose);
    enableSkip();
    writeField(wordSet, docIdLimit, "newskip4", true);
    readField(wordSet, docIdLimit, "newskip4", true, verbose);
    writeField(wordSet, docIdLimit, "newskip5", false);
    readField(wordSet, docIdLimit, "newskip5", false, verbose);
    enableSkipChunks();
    writeField(wordSet, docIdLimit, "newchunk4", true);
    readField(wordSet, docIdLimit, "newchunk4", true, verbose);
    writeField(wordSet, docIdLimit, "newchunk5", false);
    readField(wordSet, docIdLimit,
                "newchunk5",false, verbose);
    disableSkip();
    fusionField(wordSet.getNumWords(),
                docIdLimit,
                "new4", "new4x",
                false, true);
    fusionField(wordSet.getNumWords(),
                docIdLimit,
                "new4", "new4xx",
                true, true);
    fusionField(wordSet.getNumWords(),
                docIdLimit,
                "new5", "new5x",
                false, false);
    fusionField(wordSet.getNumWords(),
                docIdLimit,
                "new5", "new5xx",
                true, false);
    randReadField(wordSet, "new4", true, verbose);
    randReadField(wordSet, "new5", false, verbose);
    enableSkip();
    fusionField(wordSet.getNumWords(),
                docIdLimit,
                "newskip4", "newskip4x",
                false, true);
    fusionField(wordSet.getNumWords(),
                docIdLimit,
                "newskip4", "newskip4xx",
                true, true);
    fusionField(wordSet.getNumWords(),
                docIdLimit,
                "newskip5", "newskip5x",
                false, false);
    fusionField(wordSet.getNumWords(),
                docIdLimit,
                "newskip5", "newskip5xx",
                true, false);
    randReadField(wordSet, "newskip4", true,  verbose);
    randReadField(wordSet, "newskip5", false, verbose);
    enableSkipChunks();
    fusionField(wordSet.getNumWords(),
                           docIdLimit,
                           "newchunk4", "newchunk4x",
                           false, true);
    fusionField(wordSet.getNumWords(),
                docIdLimit,
                "newchunk4", "newchunk4xx",
                true, true);
    fusionField(wordSet.getNumWords(),
                docIdLimit,
                "newchunk5", "newchunk5x",
                false, false);
    fusionField(wordSet.getNumWords(),
                docIdLimit,
                "newchunk5", "newchunk5xx",
                true, false);
    randReadField(wordSet, "newchunk4", true, verbose);
    randReadField(wordSet, "newchunk5", false, verbose);
}


void
testFieldWriterVariantsWithHighLids(FakeWordSet &wordSet, uint32_t docIdLimit,
                             bool verbose)
{
    disableSkip();
    writeField(wordSet, docIdLimit, "hlid4", true);
    readField(wordSet, docIdLimit, "hlid4", true, verbose);
    writeField(wordSet, docIdLimit, "hlid5", false);
    readField(wordSet, docIdLimit, "hlid5", false, verbose);
    randReadField(wordSet, "hlid4", true, verbose);
    randReadField(wordSet, "hlid5", false, verbose);
    enableSkip();
    writeField(wordSet, docIdLimit, "hlidskip4", true);
    readField(wordSet, docIdLimit, "hlidskip4", true, verbose);
    writeField(wordSet, docIdLimit, "hlidskip5", false);
    readField(wordSet, docIdLimit, "hlidskip5", false, verbose);
    randReadField(wordSet, "hlidskip4", true, verbose);
    randReadField(wordSet, "hlidskip5", false, verbose);
    enableSkipChunks();
    writeField(wordSet, docIdLimit, "hlidchunk4", true);
    readField(wordSet, docIdLimit, "hlidchunk4", true, verbose);
    writeField(wordSet, docIdLimit, "hlidchunk5", false);
    readField(wordSet, docIdLimit, "hlidchunk5", false, verbose);
    randReadField(wordSet, "hlidchunk4", true, verbose);
    randReadField(wordSet, "hlidchunk5", false, verbose);
}

int
FieldWriterTest::Main()
{
    int argi;
    char c;
    const char *optArg;

    if (_argc > 0) {
        DummyFileHeaderContext::setCreator(_argv[0]);
    }
    argi = 1;

    while ((c = GetOpt("c:d:vw:", optArg, argi)) != -1) {
        switch(c) {
        case 'c':
            _commonDocFreq = atoi(optArg);
            if (_commonDocFreq == 0)
                _commonDocFreq = 1;
            break;
        case 'd':
            _numDocs = atoi(optArg);
            break;
        case 'v':
            _verbose = true;
            break;
        case 'w':
            _numWordsPerClass = atoi(optArg);
            break;
        default:
            Usage();
            return 1;
        }
    }

    if (_commonDocFreq > _numDocs) {
        Usage();
        return 1;
    }

    _wordSet.setupParams(false, false);
    _wordSet.setupWords(_rnd, _numDocs, _commonDocFreq, _numWordsPerClass);

    vespalib::mkdir("index", false);
    testFieldWriterVariants(_wordSet, _numDocs, _verbose);

    _wordSet2.setupParams(false, false);
    _wordSet2.setupWords(_rnd, _numDocs, _commonDocFreq, 3);
    uint32_t docIdBias = 700000000;
    _wordSet2.addDocIdBias(docIdBias);  // Large skip numbers
    testFieldWriterVariantsWithHighLids(_wordSet2, _numDocs + docIdBias,
                                        _verbose);
    return 0;
}

} // namespace fieldwriter

int
main(int argc, char **argv)
{
    fieldwriter::FieldWriterTest app;

    setvbuf(stdout, NULL, _IOLBF, 32768);
    app._rnd.srand48(32);
    return app.Entry(argc, argv);
}
