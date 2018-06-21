// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/fastos/app.h>
#include <iostream>
#include <getopt.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-index-inspect");

using search::TuneFileSeqRead;
using search::diskindex::DocIdMapping;
using search::diskindex::FieldReader;
using search::diskindex::PageDict4FileSeqRead;
using search::diskindex::PageDict4RandRead;
using search::diskindex::WordNumMapping;
using search::diskindex::Zc4PosOccRandRead;
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
             uint32_t elementLen, int32_t elementWeight)
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
    operator<(const PosEntry &rhs) const
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
    std::vector<search::index::WordDocElementFeatures>::const_iterator
        element = features._elements.begin();
    std::vector<search::index::WordDocElementWordPosFeatures>::
        const_iterator position = features._wordPositions.begin();
    uint32_t numElements = features._elements.size();
    while (numElements--) {
        uint32_t numOccs = element->getNumOccs();
        while (numOccs--) {
            entries.push_back(PosEntry(features._docId,
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
    std::vector<vespalib::string> _fields;
    std::vector<uint32_t> _ids;

    FieldOptions()
        : _fields(),
          _ids()
    {
    }

    void addField(const vespalib::string &field) { _fields.push_back(field); }
    bool empty() const { return _ids.empty(); }
    void validateFields(const Schema &schema);
};


void
FieldOptions::validateFields(const Schema &schema)
{
    for (std::vector<vespalib::string>::const_iterator
             i = _fields.begin(), ie = _fields.end();
         i != ie; ++i) {
        uint32_t fieldId = schema.getIndexFieldId(*i);
        if (fieldId == Schema::UNKNOWN_FIELD_ID) {
            LOG(error,
                "No such field: %s",
                i->c_str());
            exit(1);
        }
        _ids.push_back(fieldId);
    }
}


}

class SubApp
{
protected:
    FastOS_Application &_app;

public:
    SubApp(FastOS_Application &app)
        : _app(app)
    {
    }

    virtual ~SubApp() { }
    virtual void  usage(bool showHeader) = 0;
    virtual bool getOptions() = 0;
    virtual int run() = 0;
};


class ShowPostingListSubApp : public SubApp
{
    vespalib::string _indexDir;
    FieldOptions _fieldOptions;
    vespalib::string _word;
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
    ShowPostingListSubApp(FastOS_Application &app);
    virtual ~ShowPostingListSubApp();
    virtual void usage(bool showHeader) override;
    virtual bool getOptions() override;
    virtual int run() override;
    void showPostingList();
    bool readDocIdLimit(const Schema &schema);
    bool readWordList(const SchemaUtil::IndexIterator &index);
    bool readWordList(const Schema &schema);
    void readPostings(const SchemaUtil::IndexIterator &index, std::vector<PosEntry> &entries);
    void showTransposedPostingList();
};


ShowPostingListSubApp::ShowPostingListSubApp(FastOS_Application &app)
    : SubApp(app),
      _indexDir("."),
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
ShowPostingListSubApp::getOptions()
{
    int c;
    const char *optArgument = NULL;
    int longopt_index = 0;
    static struct option longopts[] = {
        { "indexdir", 1, NULL, 0 },
        { "field", 1, NULL, 0 },
        { "transpose", 0, NULL, 0 },
        { "docidlimit", 1, NULL, 0 },
        { "mindocid", 1, NULL, 0 },
        { NULL, 0, NULL, 0 }
    };
    enum longopts_enum {
        LONGOPT_INDEXDIR,
        LONGOPT_FIELD,
        LONGOPT_TRANSPOSE,
        LONGOPT_DOCIDLIMIT,
        LONGOPT_MINDOCID
    };
    int optIndex = 2;
    while ((c = _app.GetOptLong("di:mv",
                                optArgument,
                                optIndex,
                                longopts,
                                &longopt_index)) != -1) {
        switch (c) {
        case 0:
            switch (longopt_index) {
            case LONGOPT_INDEXDIR:
                _indexDir = optArgument;
                break;
            case LONGOPT_FIELD:
                _fieldOptions.addField(optArgument);
                break;
            case LONGOPT_TRANSPOSE:
                _transpose = true;
                break;
            case LONGOPT_DOCIDLIMIT:
                _docIdLimit = atoi(optArgument);
                break;
            case LONGOPT_MINDOCID:
                _minDocId = atoi(optArgument);
                break;
            default:
                if (optArgument != NULL) {
                    LOG(error,
                        "longopt %s with arg %s",
                        longopts[longopt_index].name, optArgument);
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
            _indexDir = optArgument;
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
    _optIndex = optIndex;
    if (_transpose) {
    } else {
        if (_optIndex >= _app._argc) {
            return false;
        }
        _word = _app._argv[optIndex];
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
    WordNumMapping &wm = _wmv[index.getIndex()];

    search::TuneFileSeqRead tuneFileRead;
    PageDict4FileSeqRead wr;
    vespalib::string fieldDir = _indexDir + "/" + index.getName();
    if (!wr.open(fieldDir + "/dictionary", tuneFileRead))
        return false;
    vespalib::string word;
    PostingListCounts counts;
    uint64_t wordNum = noWordNum();
    wr.readWord(word, wordNum, counts);
    words.push_back("");    // Word number 0 is special here.
    while (wordNum != noWordNumHigh()) {
        assert(wordNum == words.size());
        words.push_back(word);
        wr.readWord(word, wordNum, counts);
    }
    wm.setup(words.size() - 1);
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
        for (std::vector<uint32_t>::const_iterator
                 i = _fieldOptions._ids.begin(), ie = _fieldOptions._ids.end();
             i != ie; ++i) {
            SchemaUtil::IndexIterator index(schema, *i);
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
    vespalib::string mangledName = _indexDir + "/" + index.getName() +
                              "/";
    search::TuneFileSeqRead tuneFileRead;
    r.setup(_wmv[index.getIndex()], _dm);
    if (!r.open(mangledName, tuneFileRead))
        return;
    if (r.isValid())
        r.read();
    while (r.isValid()) {
        uint32_t docId = r._docIdAndFeatures._docId;
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
        exit(1);
    }
    _fieldOptions.validateFields(schema);
    if (!readDocIdLimit(schema))
        return;
    if (!readWordList(schema))
        return;
    std::vector<PosEntry> entries;
    if (!_fieldOptions.empty()) {
        for (std::vector<uint32_t>::const_iterator
                 i = _fieldOptions._ids.begin(), ie = _fieldOptions._ids.end();
             i != ie; ++i) {
            SchemaUtil::IndexIterator index(schema, *i);
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
    for (std::vector<PosEntry>::const_iterator
             i = entries.begin(), ie = entries.end(); i != ie; ++i) {
        if (i->_docId != prevDocId) {
            std::cout << "docId = " << i->_docId << '\n';
            prevDocId = i->_docId;
            prevFieldId = static_cast<uint32_t>(-1);
        }
        if (i->_fieldId != prevFieldId) {
            std::cout << " field = " << i->_fieldId <<
                " \"" << schema.getIndexField(i->_fieldId).getName() <<
                "\"\n";
            prevFieldId = i->_fieldId;
            prevElemId = static_cast<uint32_t>(-1);
        }
        if (i->_elementId != prevElemId ||
            i->_elementLen != prevElementLen ||
            i->_elementWeight != prevElementWeight) {
            std::cout << "  element = " << i->_elementId <<
                ", elementLen = " << i->_elementLen <<
                ", elementWeight = " << i->_elementWeight <<
                '\n';
            prevElemId = i->_elementId;
            prevElementLen = i->_elementLen;
            prevElementWeight = i->_elementWeight;
        }
        assert(i->_wordNum != 0);
        assert(i->_wordNum < _wordsv[i->_fieldId].size());
        std::cout << "   pos = " << i->_wordPos <<
            ", word = \"" << _wordsv[i->_fieldId][i->_wordNum] << "\"";
        std::cout << '\n';
    }
}


void
ShowPostingListSubApp::showPostingList()
{
    Schema schema;
    uint32_t numFields = 1;
    std::string schemaName = _indexDir + "/schema.txt";
    std::vector<vespalib::string> fieldNames;
    vespalib::string shortName;
    if (!schema.loadFromFile(schemaName)) {
        LOG(error,
            "Could not load schema from %s", schemaName.c_str());
        exit(1);
    }
    _fieldOptions.validateFields(schema);
    if (_fieldOptions._ids.size() != 1) {
        LOG(error,
            "Wrong number of field arguments: %d",
            static_cast<int>(_fieldOptions._ids.size()));
        exit(1);
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
        LOG(error,
            "Could not open dictionary %s",
            dictName.c_str());
        exit(1);
    }
    std::unique_ptr<PostingListFileRandRead> postingfile(new Zc4PosOccRandRead);
    std::string mangledName = _indexDir + "/" + shortName +
                              "/posocc.dat.compressed";
    if (!postingfile->open(mangledName, tuneFileRead)) {
        LOG(error,
            "Could not open posting list file %s",
            mangledName.c_str());
        exit(1);
    }
    PostingListOffsetAndCounts offsetAndCounts;
    uint64_t wordNum = 0;
    bool res = dict->lookup(_word, wordNum, offsetAndCounts);
    if (!res) {
        LOG(warning, "Unknown word %s", _word.c_str());
        exit(1);
    }
    if (_verbose) {
        LOG(info,
            "bitOffset %" PRId64 ", bitLen=%" PRId64 ", numDocs=%" PRId64,
            offsetAndCounts._offset,
            offsetAndCounts._counts._bitLength,
            offsetAndCounts._counts._numDocs);
    }
    typedef PostingListCounts Counts;
    typedef PostingListHandle Handle;
    typedef std::pair<Counts, Handle> CH;
    typedef std::unique_ptr<CH> CHAP;
    CHAP handle(new CH);
    handle->first = offsetAndCounts._counts;
    handle->second._bitOffset = offsetAndCounts._offset;
    handle->second._bitLength = handle->first._bitLength;
    const uint32_t first_segment = 0;
    const uint32_t num_segments = 0;    // means all segments
    handle->second._file = postingfile.get();
    handle->second._file->readPostingList(handle->first,
                                       first_segment,
                                       num_segments,
                                       handle->second);
    std::vector<TermFieldMatchData> tfmdv(numFields);
    TermFieldMatchDataArray tfmda;
    for (std::vector<TermFieldMatchData>::iterator
             tfit = tfmdv.begin(), tfite = tfmdv.end();
         tfit != tfite; ++tfit) {
        tfmda.add(&*tfit);
    }
    std::unique_ptr<SearchIterator> sb(handle->second.createIterator(
                                         handle->first, tfmda));
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
                if (md.getDocId() != docId)
                    continue;
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
        LOG(error,
            "Could not close posting list file %s",
            mangledName.c_str());
        exit(1);
    }
    if (!dict->close()) {
        LOG(error,
            "Could not close dictionary %s", dictName.c_str());
        exit(1);
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
    bool _all;
    bool _showWordNum;

public:
    DumpWordsSubApp(FastOS_Application &app);
    virtual ~DumpWordsSubApp();
    virtual void usage(bool showHeader) override;
    virtual bool getOptions() override;
    virtual int run() override;
    void dumpWords();
};


DumpWordsSubApp::DumpWordsSubApp(FastOS_Application &app)
    : SubApp(app),
      _indexDir("."),
      _fieldOptions(),
      _minNumDocs(0u),
      _verbose(false),
      _showWordNum(false)
{
}


DumpWordsSubApp::~DumpWordsSubApp()
{
}


void
DumpWordsSubApp::usage(bool showHeader)
{
    using std::cerr;
    if (showHeader)
        usageHeader();
    cerr <<
        "vespa-index-inspect dumpwords [--indexdir indexDir]\n"
        " --field field\n"
        " [--minnumdocs minnumdocs] [--verbose] [--wordnum]\n"
        "\n";
}


bool
DumpWordsSubApp::getOptions()
{
    int c;
    const char *optArgument = NULL;
    int longopt_index = 0;
    static struct option longopts[] = {
        { "indexdir", 1, NULL, 0 },
        { "field", 1, NULL, 0 },
        { "minnumdocs", 1, NULL, 0 },
        { "verbose", 0, NULL, 0 },
        { "wordnum", 0, NULL, 0 },
        { NULL, 0, NULL, 0 }
    };
    enum longopts_enum {
        LONGOPT_INDEXDIR,
        LONGOPT_FIELD,
        LONGOPT_MINNUMDOCS,
        LONGOPT_VERBOSE,
        LONGOPT_WORDNUM
    };
    int optIndex = 2;
    while ((c = _app.GetOptLong("i:",
                                optArgument,
                                optIndex,
                                longopts,
                                &longopt_index)) != -1) {
        switch (c) {
        case 0:
            switch (longopt_index) {
            case LONGOPT_INDEXDIR:
                _indexDir = optArgument;
                break;
            case LONGOPT_FIELD:
                _fieldOptions.addField(optArgument);
                break;
            case LONGOPT_MINNUMDOCS:
                _minNumDocs = atol(optArgument);
                break;
            case LONGOPT_VERBOSE:
                _verbose = true;
                break;
            case LONGOPT_WORDNUM:
                _showWordNum = true;
                break;
            default:
                if (optArgument != NULL) {
                    LOG(error,
                        "longopt %s with arg %s",
                        longopts[longopt_index].name, optArgument);
                } else {
                    LOG(error,
                        "longopt %s",
                        longopts[longopt_index].name);
                }
            }
            break;
        case 'i':
            _indexDir = optArgument;
            break;
        default:
            return false;
        }
    }
    return true;
}


void
DumpWordsSubApp::dumpWords()
{
    search::index::Schema schema;
    std::string schemaName = _indexDir + "/schema.txt";
    if (!schema.loadFromFile(schemaName)) {
        LOG(error,
            "Could not load schema from %s", schemaName.c_str());
        exit(1);
    }
    _fieldOptions.validateFields(schema);
    if (_fieldOptions._ids.size() != 1) {
        LOG(error,
            "Wrong number of field arguments: %d",
            static_cast<int>(_fieldOptions._ids.size()));
        exit(1);
    }

    SchemaUtil::IndexIterator index(schema, _fieldOptions._ids[0]);
    vespalib::string fieldDir = _indexDir + "/" + index.getName();
    PageDict4FileSeqRead wordList;
    std::string wordListName = fieldDir + "/dictionary";
    search::TuneFileSeqRead tuneFileRead;
    if (!wordList.open(wordListName, tuneFileRead)) {
        LOG(error,
            "Could not open wordlist %s", wordListName.c_str());
        exit(1);
    }
    uint64_t wordNum = 0;
    vespalib::string word;
    PostingListCounts counts;
    for (;;) {
        wordList.readWord(word, wordNum, counts);
        if (wordNum == wordList.noWordNumHigh())
            break;
        if (counts._numDocs < _minNumDocs)
            continue;
        if (_showWordNum) {
            std::cout << wordNum << '\t';
        }
        std::cout << word << '\t' << counts._numDocs;
        if (_verbose) {
            std::cout << '\t' << counts._bitLength;
        }
        std::cout << '\n';
    }
    if (!wordList.close()) {
        LOG(error,
            "Could not close wordlist %s", wordListName.c_str());
        exit(1);
    }
}


int
DumpWordsSubApp::run()
{
    dumpWords();
    return 0;
}


class VespaIndexInspectApp : public FastOS_Application
{
public:
    VespaIndexInspectApp();
    void usage();
    int Main() override;
};


VespaIndexInspectApp::VespaIndexInspectApp()
    : FastOS_Application()
{
}


void
VespaIndexInspectApp::usage()
{
    ShowPostingListSubApp(*this).usage(true);
    DumpWordsSubApp(*this).usage(false);
}


int
VespaIndexInspectApp::Main()
{
    if (_argc < 2) {
        usage();
        return 1;
    }
    std::unique_ptr<SubApp> subApp;
    if (strcmp(_argv[1], "showpostings") == 0)
        subApp.reset(new ShowPostingListSubApp(*this));
    else if (strcmp(_argv[1], "dumpwords") == 0)
        subApp.reset(new DumpWordsSubApp(*this));
    if (subApp.get() != NULL) {
        if (!subApp->getOptions()) {
            subApp->usage(true);
            return 1;
        }
        return subApp->run();
    }
    usage();
    return 1;
}

FASTOS_MAIN(VespaIndexInspectApp);
