// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/diskindex/fieldreader.h>
#include <vespa/searchlib/diskindex/fieldwriter.h>
#include <vespa/searchlib/diskindex/pagedict4file.h>
#include <vespa/searchlib/diskindex/pagedict4randread.h>
#include <vespa/searchlib/diskindex/zcposoccrandread.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/index/field_length_info.h>
#include <vespa/searchlib/index/postinglisthandle.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/test/fakedata/fakeword.h>
#include <vespa/searchlib/test/fakedata/fakewordset.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/time.h>
#include <openssl/evp.h>
#include <vespa/fastos/file.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <unistd.h>
#include <filesystem>
#include <vespa/log/log.h>
LOG_SETUP("fieldwriter_test");

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
using search::index::FieldLengthInfo;
using search::index::DummyFileHeaderContext;
using search::index::PostingListCounts;
using search::index::PostingListOffsetAndCounts;
using search::index::PostingListParams;
using search::index::Schema;
using search::index::SchemaUtil;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using search::queryeval::SearchIterator;
using vespalib::alloc::Alloc;

using namespace search::index;

// needed to resolve external symbol from httpd.h on AIX
void FastS_block_usr2() { }

namespace {

struct EvpMdCtxDeleter {
    void operator()(EVP_MD_CTX* evp_md_ctx) const noexcept {
        EVP_MD_CTX_free(evp_md_ctx);
    }
};

using EvpMdCtxPtr = std::unique_ptr<EVP_MD_CTX, EvpMdCtxDeleter>;

}

namespace fieldwriter {

uint32_t minSkipDocs = 64;
uint32_t minChunkDocs = 256_Ki;

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

const char *bool_to_str(bool val) { return (val ? "true" : "false"); }

vespalib::string
makeWordString(uint64_t wordNum)
{
    using AS = vespalib::asciistream;
    AS ws;
    ws << AS::Width(4) << AS::Fill('0') << wordNum;
    return ws.str();
}


class FieldWriterTest
{
private:
    bool _verbose;
    uint32_t _numDocs;
    uint32_t _commonDocFreq;
    uint32_t _numWordsPerClass;
    FakeWordSet _wordSet;
    FakeWordSet _wordSet2;
public:
    vespalib::Rand48 _rnd;

private:
    void Usage();
    void testFake(const std::string &postingType, FakeWord &fw);
public:
    FieldWriterTest();
    ~FieldWriterTest();
    int main(int argc, char **argv);
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
    bool _encode_interleaved_features;
    uint32_t _numWordIds;
    uint32_t _docIdLimit;
    vespalib::string _namepref;
    Schema _schema;
    uint32_t _indexId;

public:

    WrappedFieldWriter(const vespalib::string &namepref,
                       bool dynamicK,
                       bool encoce_cheap_fatures,
                       uint32_t numWordIds,
                       uint32_t docIdLimit);
    ~WrappedFieldWriter();

    void open();
    void close();
};

WrappedFieldWriter::~WrappedFieldWriter() {}

WrappedFieldWriter::WrappedFieldWriter(const vespalib::string &namepref,
                                       bool dynamicK,
                                       bool encode_interleaved_features,
                                       uint32_t numWordIds,
                                       uint32_t docIdLimit)
    : _fieldWriter(),
      _dynamicK(dynamicK),
      _encode_interleaved_features(encode_interleaved_features),
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
                       minSkipDocs, minChunkDocs,
                       _dynamicK, _encode_interleaved_features,
                       _schema, _indexId,
                       FieldLengthInfo(4.5, 42),
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


class FileChecksum
{
    unsigned char _digest[EVP_MAX_MD_SIZE];
    unsigned int  _digest_len;
public:
    FileChecksum(const vespalib::string &file_name);
    bool operator==(const FileChecksum &rhs) const {
        return ((_digest_len == rhs._digest_len) &&
                (memcmp(_digest, rhs._digest, _digest_len) == 0));
    }
};


FileChecksum::FileChecksum(const vespalib::string &file_name)
    : _digest(),
      _digest_len(0u)
{
    FastOS_File f;
    Alloc buf = Alloc::alloc(64_Ki);
    vespalib::string full_file_name(dirprefix + file_name);
    bool openres = f.OpenReadOnly(full_file_name.c_str());
    if (!openres) {
        LOG(error, "Could not open %s for sha256 checksum", full_file_name.c_str());
        LOG_ABORT("should not be reached");
    }
    int64_t flen = f.GetSize();
    int64_t remainder = flen;
    EvpMdCtxPtr md_ctx(EVP_MD_CTX_new());
    const EVP_MD* md = EVP_get_digestbyname("SHA256");
    EVP_DigestInit_ex(md_ctx.get(), md, nullptr);
    while (remainder > 0) {
        int64_t thistime =
            std::min(remainder, static_cast<int64_t>(buf.size()));
        f.ReadBuf(buf.get(), thistime);
        EVP_DigestUpdate(md_ctx.get(), buf.get(), thistime);
        remainder -= thistime;
    }
    EVP_DigestFinal_ex(md_ctx.get(), &_digest[0], &_digest_len);
    assert(_digest_len > 0u && _digest_len <= EVP_MAX_MD_SIZE);
}

void
compare_files(const vespalib::string &file_name_prefix, const vespalib::string &file_name_suffix)
{
    FileChecksum baseline_checksum(file_name_prefix + file_name_suffix);
    FileChecksum cooked_fusion_checksum(file_name_prefix + "x" + file_name_suffix);
    FileChecksum raw_fusion_checksum(file_name_prefix + "xx" + file_name_suffix);
    assert(baseline_checksum == cooked_fusion_checksum);
    assert(baseline_checksum == raw_fusion_checksum);
}

std::vector<vespalib::string> suffixes = {
    "boolocc.bdat", "boolocc.idx",
    "posocc.dat.compressed",
    "dictionary.pdat", "dictionary.spdat", "dictionary.ssdat"
};

void
check_fusion(const vespalib::string &file_name_prefix)
{
    for (const auto &file_name_suffix : suffixes) {
        compare_files(file_name_prefix, file_name_suffix);
    }
}

void
remove_field(const vespalib::string &file_name_prefix)
{
    vespalib::string remove_prefix(dirprefix + file_name_prefix);
    FieldWriter::remove(remove_prefix);
    FieldWriter::remove(remove_prefix + "x");
    FieldWriter::remove(remove_prefix + "xx");
}

void
writeField(FakeWordSet &wordSet,
           uint32_t docIdLimit,
           const std::string &namepref,
           bool dynamicK, bool encode_interleaved_features)
{
    const char *dynamicKStr = dynamicK ? "true" : "false";

    LOG(info,
        "enter writeField, "
        "namepref=%s, dynamicK=%s, encode_interleaved_features=%s",
        namepref.c_str(),
        dynamicKStr,
        bool_to_str(encode_interleaved_features));
    vespalib::Timer tv;
    WrappedFieldWriter ostate(namepref,
                              dynamicK, encode_interleaved_features,
                              wordSet.getNumWords(), docIdLimit);
    FieldWriter::remove(dirprefix + namepref);
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

    LOG(info,
        "leave writeField, "
        "namepref=%s, dynamicK=%s, encode_interleaved_features=%s"
        " elapsed=%10.6f",
        namepref.c_str(),
        dynamicKStr,
        bool_to_str(encode_interleaved_features),
        vespalib::to_s(tv.elapsed()));
}


void
readField(FakeWordSet &wordSet,
          uint32_t docIdLimit,
          const std::string &namepref,
          bool dynamicK,
          bool decode_interleaved_features,
          bool verbose)
{
    const char *dynamicKStr = dynamicK ? "true" : "false";

    WrappedFieldReader istate(namepref, wordSet.getNumWords(), docIdLimit);
    LOG(info, "enter readField, namepref=%s, dynamicK=%s, decode_interleaved_features=%s",
        namepref.c_str(), dynamicKStr, bool_to_str(decode_interleaved_features));

    vespalib::Timer tv;
    istate.open();
    if (istate._fieldReader->isValid())
        istate._fieldReader->read();

    auto field_length_info = istate._fieldReader->get_field_length_info();
    assert(4.5 == field_length_info.get_average_field_length());
    assert(42u == field_length_info.get_num_samples());

    TermFieldMatchData mdfield1;

    unsigned int wordNum = 1;
    for (const auto& words : wordSet.words()) {
        for (const auto& word : words) {
            TermFieldMatchDataArray tfmda;
            tfmda.add(&mdfield1);

            word->validate(*istate._fieldReader, wordNum, tfmda, decode_interleaved_features, verbose);
            ++wordNum;
        }
    }

    istate.close();
    LOG(info, "leave readField, namepref=%s, dynamicK=%s, decode_interleaved_features=%s elapsed=%10.6f",
        namepref.c_str(), dynamicKStr,
        bool_to_str(decode_interleaved_features),
        vespalib::to_s(tv.elapsed()));
}


void
randReadField(FakeWordSet &wordSet,
              const std::string &namepref,
              bool dynamicK,
              bool decode_interleaved_features,
              bool verbose)
{
    const char *dynamicKStr = dynamicK ? "true" : "false";

    PostingListCounts counts;

    LOG(info, "enter randReadField, namepref=%s, dynamicK=%s, decode_interleaved_features=%s",
        namepref.c_str(), dynamicKStr, bool_to_str(decode_interleaved_features));

    vespalib::Timer tv;

    std::string cname = dirprefix + namepref;
    cname += "dictionary";

    auto dictFile = std::make_unique<PageDict4RandRead>();

    search::index::PostingListFileRandRead *postingFile = nullptr;
    if (dynamicK)
        postingFile = new search::diskindex::ZcPosOccRandRead;
    else
        postingFile = new search::diskindex::Zc4PosOccRandRead;

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
    auto field_length_info = postingFile->get_field_length_info();
    assert(4.5 == field_length_info.get_average_field_length());
    assert(42u == field_length_info.get_num_samples());

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
                word->validate(sb.get(), tfmda, true, decode_interleaved_features, verbose);
                word->validate(sb.get(), tfmda, 19, true, decode_interleaved_features, verbose);
                word->validate(sb.get(), tfmda, 99, true, decode_interleaved_features, verbose);
                word->validate(sb.get(), tfmda, 799, true, decode_interleaved_features, verbose);
                word->validate(sb.get(), tfmda, 6399, true, decode_interleaved_features, verbose);
                word->validate(sb.get(), tfmda, 11999, true, decode_interleaved_features, verbose);
                ++wordNum;
            }
        }
    }

    postingFile->close();
    dictFile->close();
    delete postingFile;
    dictFile.reset();
    LOG(info, "leave randReadField, namepref=%s, dynamicK=%s, decode_interleaved_features=%s, elapsed=%10.6f",
        namepref.c_str(),
        dynamicKStr,
        bool_to_str(decode_interleaved_features),
        vespalib::to_s(tv.elapsed()));
}


void
fusionField(uint32_t numWordIds,
            uint32_t docIdLimit,
            const vespalib::string &ipref,
            const vespalib::string &opref,
            bool doRaw,
            bool dynamicK,
            bool encode_interleaved_features)
{
    const char *rawStr = doRaw ? "true" : "false";
    const char *dynamicKStr = dynamicK ? "true" : "false";


    LOG(info,
        "enter fusionField, ipref=%s, opref=%s,"
        " raw=%s,"
        " dynamicK=%s, encode_interleaved_features=%s",
        ipref.c_str(),
        opref.c_str(),
        rawStr,
        dynamicKStr, bool_to_str(encode_interleaved_features));

    WrappedFieldWriter ostate(opref, dynamicK, encode_interleaved_features, numWordIds, docIdLimit);
    WrappedFieldReader istate(ipref, numWordIds, docIdLimit);

    vespalib::Timer tv;

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

    LOG(info,
        "leave fusionField, ipref=%s, opref=%s,"
        " raw=%s dynamicK=%s, encode_interleaved_features=%s,"
        " elapsed=%10.6f",
        ipref.c_str(),
        opref.c_str(),
        rawStr,
        dynamicKStr, bool_to_str(encode_interleaved_features),
        vespalib::to_s(tv.elapsed()));
}


void
testFieldWriterVariant(FakeWordSet &wordSet, uint32_t doc_id_limit,
                       const vespalib::string &file_name_prefix,
                       bool dynamic_k,
                       bool encode_interleaved_features,
                       bool verbose)
{
    writeField(wordSet, doc_id_limit, file_name_prefix, dynamic_k, encode_interleaved_features);
    readField(wordSet, doc_id_limit, file_name_prefix, dynamic_k, encode_interleaved_features, verbose);
    randReadField(wordSet, file_name_prefix, dynamic_k, encode_interleaved_features, verbose);
    fusionField(wordSet.getNumWords(),
                doc_id_limit,
                file_name_prefix, file_name_prefix + "x",
                false, dynamic_k, encode_interleaved_features);
    fusionField(wordSet.getNumWords(),
                doc_id_limit,
                file_name_prefix, file_name_prefix + "xx",
                true, dynamic_k, encode_interleaved_features);
    check_fusion(file_name_prefix);
    remove_field(file_name_prefix);
}

void
testFieldWriterVariants(FakeWordSet &wordSet,
                        uint32_t docIdLimit, bool verbose)
{
    disableSkip();
    testFieldWriterVariant(wordSet, docIdLimit, "new4", true, false, verbose);
    testFieldWriterVariant(wordSet, docIdLimit, "new5", false, false, verbose);
    enableSkip();
    testFieldWriterVariant(wordSet, docIdLimit, "newskip4", true, false, verbose);
    testFieldWriterVariant(wordSet, docIdLimit, "newskip5", false, false, verbose);
    enableSkipChunks();
    testFieldWriterVariant(wordSet, docIdLimit, "newchunk4", true, false, verbose);
    testFieldWriterVariant(wordSet, docIdLimit, "newchunk5", false, false, verbose);
    testFieldWriterVariant(wordSet, docIdLimit, "newchunkcf4", true, true, verbose);
}


void
testFieldWriterVariantsWithHighLids(FakeWordSet &wordSet, uint32_t docIdLimit,
                             bool verbose)
{
    disableSkip();
    testFieldWriterVariant(wordSet, docIdLimit, "hlid4", true, false, verbose);
    testFieldWriterVariant(wordSet, docIdLimit, "hlid5", false, false, verbose);
    enableSkip();
    testFieldWriterVariant(wordSet, docIdLimit, "hlidskip4", true, false, verbose);
    testFieldWriterVariant(wordSet, docIdLimit, "hlidskip5", false, false, verbose);
    enableSkipChunks();
    testFieldWriterVariant(wordSet, docIdLimit, "hlidchunk4", true, false, verbose);
    testFieldWriterVariant(wordSet, docIdLimit, "hlidchunk5", false, false, verbose);
}

int
FieldWriterTest::main(int argc, char **argv)
{
    int c;

    if (argc > 0) {
        DummyFileHeaderContext::setCreator(argv[0]);
    }

    while ((c = getopt(argc, argv, "c:d:vw:")) != -1) {
        switch(c) {
        case 'c':
            _commonDocFreq = atoi(optarg);
            if (_commonDocFreq == 0)
                _commonDocFreq = 1;
            break;
        case 'd':
            _numDocs = atoi(optarg);
            break;
        case 'v':
            _verbose = true;
            break;
        case 'w':
            _numWordsPerClass = atoi(optarg);
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

    std::filesystem::create_directory(std::filesystem::path("index"));
    testFieldWriterVariants(_wordSet, _numDocs, _verbose);

    _wordSet2.setupParams(false, false);
    _wordSet2.setupWords(_rnd, _numDocs, _commonDocFreq, 3);
    uint32_t docIdBias = 700000000;
    _wordSet2.addDocIdBias(docIdBias);  // Large skip numbers
    testFieldWriterVariantsWithHighLids(_wordSet2, _numDocs + docIdBias,
                                        _verbose);
    std::filesystem::remove_all(std::filesystem::path("index"));
    return 0;
}

} // namespace fieldwriter

int
main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    fieldwriter::FieldWriterTest app;

    setvbuf(stdout, nullptr, _IOLBF, 32_Ki);
    app._rnd.srand48(32);
    return app.main(argc, argv);
}
