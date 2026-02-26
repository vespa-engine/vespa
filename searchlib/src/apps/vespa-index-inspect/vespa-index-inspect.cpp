// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/file_interface.h>
#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/diskindex/pagedict4randread.h>
#include <vespa/searchlib/diskindex/pagedict4file.h>
#include <vespa/searchlib/diskindex/zcposoccrandread.h>
#include <vespa/searchlib/diskindex/docidmapper.h>
#include <vespa/searchlib/diskindex/wordnummapper.h>
#include <vespa/searchlib/diskindex/fieldreader.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <iostream>
#include <getopt.h>
#include <cstdlib>
#include <cinttypes>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-index-inspect");

using search::FileUtil;
using search::TuneFileSeqRead;
using search::diskindex::DocIdMapping;
using search::diskindex::FieldReader;
using search::diskindex::PageDict4FileSeqRead;
using search::diskindex::PageDict4RandRead;
using search::diskindex::WordNumMapping;
using search::diskindex::Zc4PosOccRandRead;
using search::diskindex::ZcPosOccRandRead;
using search::fef::FieldPositionsIterator;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::index::DictionaryFileRandRead;
using search::index::DocIdAndFeatures;
using search::index::PostingListCounts;
using search::index::PostingListFileRandRead;
using search::index::PostingListHandle;
using search::index::PostingListOffsetAndCounts;
using search::index::Schema;
using search::index::SchemaUtil;
using search::index::schema::DataType;
using search::queryeval::SearchIterator;
using namespace search::index;
using vespalib::FileHeader;

namespace {

/**
 * Fine granularity, for small scale inversion within a single document.
 */
class PosEntry
{
public:
    uint32_t _docId;
    uint32_t _fieldId;
    uint64_t _wordNum;
    uint32_t _elementId;
    uint32_t _wordPos;
    uint32_t _elementLen;
    int32_t _elementWeight;

    PosEntry(uint32_t docId,
             uint32_t fieldId,
             uint32_t elementId, uint32_t wordPos,
             uint64_t wordNum,
             uint32_t elementLen, int32_t elementWeight) noexcept
        : _docId(docId),
          _fieldId(fieldId),
          _wordNum(wordNum),
          _elementId(elementId),
          _wordPos(wordPos),
          _elementLen(elementLen),
          _elementWeight(elementWeight)
    {
    }

    bool
    operator<(const PosEntry &rhs) const noexcept
    {
        if (_docId != rhs._docId)
            return _docId < rhs._docId;
        if (_fieldId != rhs._fieldId)
            return _fieldId < rhs._fieldId;
        if (_elementId != rhs._elementId)
            return _elementId < rhs._elementId;
        if (_wordPos != rhs._wordPos)
            return _wordPos < rhs._wordPos;
        return _wordNum < rhs._wordNum;
    }
};


void
unpackFeatures(std::vector<PosEntry> &entries,
               uint32_t fieldId,
               uint64_t wordNum,
               const DocIdAndFeatures &features)
{
    auto element = features.elements().begin();
    auto position = features.word_positions().begin();
    uint32_t numElements = features.elements().size();
    while (numElements--) {
        uint32_t numOccs = element->getNumOccs();
        while (numOccs--) {
            entries.push_back(PosEntry(features.doc_id(),
                                       fieldId,
                                       element->getElementId(),
                                       position->getWordPos(),
                                       wordNum,
                                       element->getElementLen(),
                                       element->getWeight()));
            ++position;
        }
        ++element;
    }
}


void
usageHeader()
{
    using std::cerr;
    cerr <<
        "vespa-index-inspect version 0.0\n"
        "\n"
        "USAGE:\n";
}


class FieldOptions
{
public:
    std::vector<std::string> _fields;
    std::vector<uint32_t> _ids;

    FieldOptions()
        : _fields(),
          _ids()
    {
    }
    ~FieldOptions();

    void addField(const std::string &field) { _fields.push_back(field); }
    bool empty() const { return _ids.empty(); }
    void validateFields(const Schema &schema);
};

FieldOptions::~FieldOptions() = default;

void
FieldOptions::validateFields(const Schema &schema)
{
    for (const auto& field : _fields) {
        uint32_t fieldId = schema.getIndexFieldId(field);
        if (fieldId == Schema::UNKNOWN_FIELD_ID) {
            LOG(error, "No such field: %s", field.c_str());
            std::_Exit(1);
        }
        _ids.push_back(fieldId);
    }
}

const std::string posocc_file_name = "posocc.dat.compressed";

}

class SubApp
{
public:
    virtual ~SubApp() = default;
    virtual void  usage(bool showHeader) = 0;
    virtual bool getOptions(int argc, char **argv) = 0;
    virtual int run() = 0;
};


class ShowPostingListSubApp : public SubApp
{
    std::string _indexDir;
    FieldOptions _fieldOptions;
    std::string _word;
    bool _verbose;
    bool _readmmap;
    bool _directio;
    bool _transpose;
    int _optIndex;
    DocIdMapping _dm;
    std::vector<WordNumMapping> _wmv;
    std::vector<std::vector<std::string>>  _wordsv;
    uint32_t _docIdLimit;
    uint32_t _minDocId;

    static uint64_t noWordNumHigh() { return std::numeric_limits<uint64_t>::max(); }
    static uint64_t noWordNum() { return 0u; }
public:
    ShowPostingListSubApp();
    ~ShowPostingListSubApp() override;
    void usage(bool showHeader) override;
    bool getOptions(int argc, char **argv) override;
    int run() override;
    void showPostingList();
    bool readDocIdLimit(const Schema &schema);
    bool readWordList(const SchemaUtil::IndexIterator &index);
    bool readWordList(const Schema &schema);
    void readPostings(const SchemaUtil::IndexIterator &index, std::vector<PosEntry> &entries);
    void showTransposedPostingList();
};


ShowPostingListSubApp::ShowPostingListSubApp()
    : _indexDir("."),
      _fieldOptions(),
      _word(),
      _verbose(false),
      _readmmap(false),
      _directio(false),
      _transpose(false),
      _optIndex(1),
      _dm(),
      _wmv(),
      _wordsv(),
      _docIdLimit(std::numeric_limits<uint32_t>::max()),
      _minDocId(0u)
{
}


ShowPostingListSubApp::~ShowPostingListSubApp()
{
}


void
ShowPostingListSubApp::usage(bool showHeader)
{
    using std::cerr;
    if (showHeader)
        usageHeader();
    cerr <<
        "vespa-index-inspect showpostings [--indexdir indexDir]\n"
        " --field field\n"
        " word\n"
        "\n"
        "vespa-index-inspect showpostings [--indexdir indexDir]\n"
        " [--field field]\n"
        " --transpose\n"
        " [--docidlimit docIdLimit] [--mindocid mindocid]\n"
        "\n";
}


bool
ShowPostingListSubApp::getOptions(int argc, char **argv)
{
    int c;
    int longopt_index = 0;
    static struct option longopts[] = {
        { "indexdir", 1, nullptr, 0 },
        { "field", 1, nullptr, 0 },
        { "transpose", 0, nullptr, 0 },
        { "docidlimit", 1, nullptr, 0 },
        { "mindocid", 1, nullptr, 0 },
        { nullptr, 0, nullptr, 0 }
    };
    enum longopts_enum {
        LONGOPT_INDEXDIR,
        LONGOPT_FIELD,
        LONGOPT_TRANSPOSE,
        LONGOPT_DOCIDLIMIT,
        LONGOPT_MINDOCID
    };
    optind = 2;
    while ((c = getopt_long(argc, argv, "di:mv",
                            longopts,
                            &longopt_index)) != -1) {
        switch (c) {
        case 0:
            switch (longopt_index) {
            case LONGOPT_INDEXDIR:
                _indexDir = optarg;
                break;
            case LONGOPT_FIELD:
                _fieldOptions.addField(optarg);
                break;
            case LONGOPT_TRANSPOSE:
                _transpose = true;
                break;
            case LONGOPT_DOCIDLIMIT:
                _docIdLimit = atoi(optarg);
                break;
            case LONGOPT_MINDOCID:
                _minDocId = atoi(optarg);
                break;
            default:
                if (optarg != nullptr) {
                    LOG(error,
                        "longopt %s with arg %s",
                        longopts[longopt_index].name, optarg);
                } else {
                    LOG(error,
                        "longopt %s",
                        longopts[longopt_index].name);
                }
            }
            break;
        case 'd':
            _directio = true;
            break;
        case 'i':
            _indexDir = optarg;
            break;
        case 'm':
            _readmmap = true;
            break;
        case 'v':
            _verbose = true;
            break;
        default:
            return false;
        }
    }
    if (_transpose) {
    } else {
        if (_fieldOptions._fields.empty())
            return false;
        if (_fieldOptions._fields.size() > 1)
            return false;
    }
    _optIndex = optind;
    if (_transpose) {
    } else {
        if (_optIndex >= argc) {
            return false;
        }
        _word = argv[optind];
    }
    return true;
}


bool
ShowPostingListSubApp::readDocIdLimit(const Schema &schema)
{
    TuneFileSeqRead tuneFileRead;
    if (_dm.readDocIdLimit(_indexDir))
        return true;
    uint32_t numIndexFields = schema.getNumIndexFields();
    for (uint32_t fieldId = 0; fieldId < numIndexFields; ++fieldId) {
        const Schema::IndexField &field = schema.getIndexField(fieldId);
        if (field.getDataType() == DataType::STRING) {
            FieldReader fr;
            if (!fr.open(_indexDir + "/" + field.getName() + "/",
                         tuneFileRead))
                continue;
            _dm.setup(fr.getDocIdLimit());
            return true;
        }
    }
    return false;
}


bool
ShowPostingListSubApp::readWordList(const SchemaUtil::IndexIterator &index)
{
    std::vector<std::string> &words = _wordsv[index.getIndex()];

    search::TuneFileSeqRead tuneFileRead;
    PageDict4FileSeqRead wr;
    std::string fieldDir = _indexDir + "/" + index.getName();
    if (!wr.open(fieldDir + "/dictionary", tuneFileRead))
        return false;
    std::string word;
    PostingListCounts counts;
    uint64_t wordNum = noWordNum();
    wr.readWord(word, wordNum, counts);
    words.push_back("");    // Word number 0 is special here.
    while (wordNum != noWordNumHigh()) {
        assert(wordNum == words.size());
        words.push_back(word);
        wr.readWord(word, wordNum, counts);
    }
    if (!wr.close())
        return false;
    return true;
}

bool
ShowPostingListSubApp::readWordList(const Schema &schema)
{
    _wordsv.clear();
    _wmv.clear();
    uint32_t numFields = schema.getNumIndexFields();
    _wordsv.resize(numFields);
    _wmv.resize(numFields);

    if (!_fieldOptions.empty()) {
        for (auto id : _fieldOptions._ids) {
            SchemaUtil::IndexIterator index(schema, id);
            if (!readWordList(index))
                return false;
        }
    } else {
        SchemaUtil::IndexIterator index(schema);
        while (index.isValid()) {
            if (!readWordList(index))
                return false;
            ++index;
        }
    }
    return true;
}


void
ShowPostingListSubApp::readPostings(const SchemaUtil::IndexIterator &index,
                                    std::vector<PosEntry> &entries)
{
    FieldReader r;
    std::unique_ptr<PostingListFileRandRead> postingfile(new Zc4PosOccRandRead);
    std::string mangledName = _indexDir + "/" + index.getName() +
                              "/";
    search::TuneFileSeqRead tuneFileRead;
    r.setup(_wmv[index.getIndex()], _dm);
    if (!r.open(mangledName, tuneFileRead))
        return;
    if (r.isValid())
        r.read();
    while (r.isValid()) {
        uint32_t docId = r._docIdAndFeatures.doc_id();
        if (docId >= _minDocId && docId < _docIdLimit) {
            unpackFeatures(entries, index.getIndex(),
                           r._wordNum, r._docIdAndFeatures);
        }
        r.read();
    }
    if (!r.close())
        LOG_ABORT("should not be reached");
}


void
ShowPostingListSubApp::showTransposedPostingList()
{
    Schema schema;
    std::string schemaName = _indexDir + "/schema.txt";
    if (!schema.loadFromFile(schemaName)) {
        LOG(error,
            "Could not load schema from %s", schemaName.c_str());
        std::_Exit(1);
    }
    _fieldOptions.validateFields(schema);
    if (!readDocIdLimit(schema))
        return;
    if (!readWordList(schema))
        return;
    std::vector<PosEntry> entries;
    if (!_fieldOptions.empty()) {
        for (auto id : _fieldOptions._ids) {
            SchemaUtil::IndexIterator index(schema, id);
            readPostings(index, entries);
        }
    } else {
        SchemaUtil::IndexIterator index(schema);
        while (index.isValid()) {
            readPostings(index, entries);
            ++index;
        }
    }
    std::sort(entries.begin(), entries.end());
    uint32_t prevDocId = static_cast<uint32_t>(-1);
    uint32_t prevFieldId = static_cast<uint32_t>(-1);
    uint32_t prevElemId = static_cast<uint32_t>(-1);
    uint32_t prevElementLen = 0;
    int32_t prevElementWeight = 0;
    for (const auto& entry : entries) {
        if (entry._docId != prevDocId) {
            std::cout << "docId = " << entry._docId << '\n';
            prevDocId = entry._docId;
            prevFieldId = static_cast<uint32_t>(-1);
        }
        if (entry._fieldId != prevFieldId) {
            std::cout << " field = " << entry._fieldId <<
                " \"" << schema.getIndexField(entry._fieldId).getName() <<
                "\"\n";
            prevFieldId = entry._fieldId;
            prevElemId = static_cast<uint32_t>(-1);
        }
        if (entry._elementId != prevElemId ||
            entry._elementLen != prevElementLen ||
            entry._elementWeight != prevElementWeight) {
            std::cout << "  element = " << entry._elementId <<
                ", elementLen = " << entry._elementLen <<
                ", elementWeight = " << entry._elementWeight <<
                '\n';
            prevElemId = entry._elementId;
            prevElementLen = entry._elementLen;
            prevElementWeight = entry._elementWeight;
        }
        assert(entry._wordNum != 0);
        assert(entry._wordNum < _wordsv[entry._fieldId].size());
        std::cout << "   pos = " << entry._wordPos <<
            ", word = \"" << _wordsv[entry._fieldId][entry._wordNum] << "\"";
        std::cout << '\n';
    }
}


void
ShowPostingListSubApp::showPostingList()
{
    Schema schema;
    uint32_t numFields = 1;
    std::string schemaName = _indexDir + "/schema.txt";
    std::vector<std::string> fieldNames;
    std::string shortName;
    if (!schema.loadFromFile(schemaName)) {
        LOG(error,
            "Could not load schema from %s", schemaName.c_str());
        std::_Exit(1);
    }
    _fieldOptions.validateFields(schema);
    if (_fieldOptions._ids.size() != 1) {
        LOG(error,
            "Wrong number of field arguments: %d",
            static_cast<int>(_fieldOptions._ids.size()));
        std::_Exit(1);
    }
    SchemaUtil::IndexIterator it(schema, _fieldOptions._ids.front());

    shortName = it.getName();
    fieldNames.push_back(it.getName());
    std::unique_ptr<DictionaryFileRandRead> dict(new PageDict4RandRead);
    std::string dictName = _indexDir + "/" + shortName + "/dictionary";
    search::TuneFileRandRead tuneFileRead;
    if (_directio)
        tuneFileRead.setWantDirectIO();
    if (_readmmap)
        tuneFileRead.setWantMemoryMap();
    if (!dict->open(dictName, tuneFileRead)) {
        LOG(error, "Could not open dictionary %s", dictName.c_str());
        std::_Exit(1);
    }
    std::unique_ptr<PostingListFileRandRead> postingfile(new Zc4PosOccRandRead);
    std::string mangledName = _indexDir + "/" + shortName +
                              "/" + posocc_file_name;
    if (!postingfile->open(mangledName, tuneFileRead)) {
        LOG(error, "Could not open posting list file %s", mangledName.c_str());
        std::_Exit(1);
    }
    PostingListOffsetAndCounts offsetAndCounts;
    uint64_t wordNum = 0;
    bool res = dict->lookup(_word, wordNum, offsetAndCounts);
    if (!res) {
        LOG(warning, "Unknown word %s", _word.c_str());
        std::_Exit(1);
    }
    if (_verbose) {
        LOG(info,
            "bitOffset %" PRId64 ", bitLen=%" PRId64 ", numDocs=%" PRId64,
            offsetAndCounts._offset,
            offsetAndCounts._counts._bitLength,
            offsetAndCounts._counts._numDocs);
    }
    using Handle = PostingListHandle;
    using CH = std::pair<DictionaryLookupResult, Handle>;
    using CHAP = std::unique_ptr<CH>;
    CHAP handle(new CH);
    handle->first.wordNum = wordNum;
    handle->first.counts = offsetAndCounts._counts;
    handle->first.bitOffset = offsetAndCounts._offset;
    handle->second = postingfile->read_posting_list(handle->first);
    std::vector<TermFieldMatchData> tfmdv(numFields);
    TermFieldMatchDataArray tfmda;
    for (auto& tfmd : tfmdv) {
        tfmda.add(&tfmd);
    }
    auto sb = postingfile->createIterator(handle->first, handle->second, tfmda);
    sb->initFullRange();
    uint32_t docId = 0;
    bool first = true;
    for (;;) {
        if (sb->seek(docId)) {
            first = false;
            std::cout << "docId = " << docId << '\n';
            sb->unpack(docId);
            for (uint32_t field = 0; field < numFields; ++field) {
                const TermFieldMatchData &md = *tfmda[field];
                if (!md.has_ranking_data(docId)) {
                    continue;
                }
                std::cout << " field = " << fieldNames[field] << '\n';
                FieldPositionsIterator fpi = md.getIterator();
                uint32_t lastElement = static_cast<uint32_t>(-1);
                while (fpi.valid()) {
                    if (fpi.getElementId() != lastElement) {
                        std::cout << "  element = " << fpi.getElementId() <<
                            ", elementLen = " << fpi.getElementLen() <<
                            ", elementWeight = " << fpi.getElementWeight() <<
                            '\n';
                        lastElement = fpi.getElementId();
                    }
                    std::cout << "   pos = " << fpi.getPosition() << '\n';
                    fpi.next();
                }
            }
            ++docId;
        } else {
            docId = sb->getDocId();
            if (sb->isAtEnd())
                break;
        }
    }
    if (first) {
        std::cout << "No hits\n";
    }

    if (!postingfile->close()) {
        LOG(error, "Could not close posting list file %s",
            mangledName.c_str());
        std::_Exit(1);
    }
    if (!dict->close()) {
        LOG(error, "Could not close dictionary %s", dictName.c_str());
        std::_Exit(1);
    }
}


int
ShowPostingListSubApp::run()
{
    if (_transpose)
        showTransposedPostingList();
    else
        showPostingList();
    return 0;
}


class DumpWordsSubApp : public SubApp
{
    std::string _indexDir;
    FieldOptions _fieldOptions;
    uint64_t _minNumDocs;
    bool _verbose;
    bool _showWordNum;
    bool _file_range;
    uint64_t _posocc_file_header_bit_size;

    void extract_posocc_file_header_bit_size(const std::string& field_dir);
public:
    DumpWordsSubApp();
    ~DumpWordsSubApp() override;
    void usage(bool showHeader) override;
    bool getOptions(int argc, char **argv) override;
    int run() override;
    void dumpWords();
};


DumpWordsSubApp::DumpWordsSubApp()
    : _indexDir("."),
      _fieldOptions(),
      _minNumDocs(0u),
      _verbose(false),
      _showWordNum(false),
      _file_range(false),
      _posocc_file_header_bit_size(0)
{
}


DumpWordsSubApp::~DumpWordsSubApp() = default;


void
DumpWordsSubApp::usage(bool showHeader)
{
    using std::cerr;
    if (showHeader)
        usageHeader();
    cerr <<
        "vespa-index-inspect dumpwords [--indexdir indexDir]\n"
        " --field field\n"
        " [--file-range] [--minnumdocs minnumdocs] [--verbose] [--wordnum]\n"
        "\n";
}


bool
DumpWordsSubApp::getOptions(int argc, char **argv)
{
    int c;
    int longopt_index = 0;
    static struct option longopts[] = {
        { "indexdir", 1, nullptr, 0 },
        { "field", 1, nullptr, 0 },
        { "file-range", 0, nullptr, 0 },
        { "minnumdocs", 1, nullptr, 0 },
        { "verbose", 0, nullptr, 0 },
        { "wordnum", 0, nullptr, 0 },
        { nullptr, 0, nullptr, 0 }
    };
    enum longopts_enum {
        LONGOPT_INDEXDIR,
        LONGOPT_FIELD,
        LONGOPT_FILE_RANGE,
        LONGOPT_MINNUMDOCS,
        LONGOPT_VERBOSE,
        LONGOPT_WORDNUM
    };
    optind = 2;
    while ((c = getopt_long(argc, argv, "i:",
                            longopts,
                            &longopt_index)) != -1) {
        switch (c) {
        case 0:
            switch (longopt_index) {
            case LONGOPT_INDEXDIR:
                _indexDir = optarg;
                break;
            case LONGOPT_FIELD:
                _fieldOptions.addField(optarg);
                break;
            case LONGOPT_FILE_RANGE:
                _file_range = true;
                break;
            case LONGOPT_MINNUMDOCS:
                _minNumDocs = atol(optarg);
                break;
            case LONGOPT_VERBOSE:
                _verbose = true;
                break;
            case LONGOPT_WORDNUM:
                _showWordNum = true;
                break;
            default:
                if (optarg != nullptr) {
                    LOG(error,
                        "longopt %s with arg %s",
                        longopts[longopt_index].name, optarg);
                } else {
                    LOG(error,
                        "longopt %s",
                        longopts[longopt_index].name);
                }
            }
            break;
        case 'i':
            _indexDir = optarg;
            break;
        default:
            return false;
        }
    }
    return true;
}

void
DumpWordsSubApp::extract_posocc_file_header_bit_size(const std::string& field_dir)
{
    std::string file_name = field_dir + "/" + posocc_file_name;
    auto df = FileUtil::openFile(file_name);
    FileHeader header;
    auto header_size = header.readFile(*df);
    _posocc_file_header_bit_size = header_size * 8;
}

void
DumpWordsSubApp::dumpWords()
{
    search::index::Schema schema;
    std::string schemaName = _indexDir + "/schema.txt";
    if (!schema.loadFromFile(schemaName)) {
        LOG(error, "Could not load schema from %s", schemaName.c_str());
        std::_Exit(1);
    }
    _fieldOptions.validateFields(schema);
    if (_fieldOptions._ids.size() != 1) {
        LOG(error, "Wrong number of field arguments: %d",
            static_cast<int>(_fieldOptions._ids.size()));
        std::_Exit(1);
    }

    SchemaUtil::IndexIterator index(schema, _fieldOptions._ids[0]);
    std::string fieldDir = _indexDir + "/" + index.getName();
    PageDict4FileSeqRead wordList;
    std::string wordListName = fieldDir + "/dictionary";
    search::TuneFileSeqRead tuneFileRead;
    if (!wordList.open(wordListName, tuneFileRead)) {
        LOG(error, "Could not open wordlist %s", wordListName.c_str());
        std::_Exit(1);
    }
    if (_file_range) {
        extract_posocc_file_header_bit_size(fieldDir);
    }
    uint64_t wordNum = 0;
    std::string word;
    DictionaryLookupResult lookup_result;
    auto& counts = lookup_result.counts;
    for (;;) {
        lookup_result.bitOffset += counts._bitLength;
        wordList.readWord(word, wordNum, counts);
        if (wordNum == wordList.noWordNumHigh())
            break;
        if (counts._numDocs < _minNumDocs) {
            continue;
        }
        if (_showWordNum) {
            std::cout << wordNum << '\t';
        }
        std::cout << word << '\t' << counts._numDocs;
        if (_file_range) {
            auto range = ZcPosOccRandRead::get_posting_list_file_range(lookup_result, _posocc_file_header_bit_size);
            std::cout << '\t' << range.start_offset << '\t' << range.size();
        }
        if (_verbose) {
            std::cout << '\t' << counts._bitLength;
        }
        std::cout << '\n';
    }
    if (!wordList.close()) {
        LOG(error, "Could not close wordlist %s", wordListName.c_str());
        std::_Exit(1);
    }
}


int
DumpWordsSubApp::run()
{
    dumpWords();
    return 0;
}


class VespaIndexInspectApp
{
public:
    void usage();
    int main(int argc, char **argv);
};


void
VespaIndexInspectApp::usage()
{
    ShowPostingListSubApp().usage(true);
    DumpWordsSubApp().usage(false);
}


int
VespaIndexInspectApp::main(int argc, char **argv)
{
    if (argc < 2) {
        usage();
        return 1;
    }
    std::unique_ptr<SubApp> subApp;
    if (strcmp(argv[1], "showpostings") == 0)
        subApp = std::make_unique<ShowPostingListSubApp>();
    else if (strcmp(argv[1], "dumpwords") == 0)
        subApp = std::make_unique<DumpWordsSubApp>();
    if (subApp.get() != nullptr) {
        if (!subApp->getOptions(argc, argv)) {
            subApp->usage(true);
            return 1;
        }
        return subApp->run();
    }
    usage();
    return 1;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    VespaIndexInspectApp app;
    return app.main(argc, argv);
}
