// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexbuilder.h"
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/common/documentsummary.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/searchlib/diskindex/fieldwriter.h>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/error.h>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.indexbuilder");


namespace search {

namespace diskindex {

namespace {

using common::FileHeaderContext;
using index::DocIdAndFeatures;
using index::PostingListCounts;
using index::Schema;
using index::SchemaUtil;
using index::WordDocElementFeatures;
using index::schema::DataType;
using vespalib::getLastErrorString;

static uint32_t
noWordPos()
{
    return std::numeric_limits<uint32_t>::max();
}


class FileHandle
{
public:
    FieldWriter *_fieldWriter;
    DocIdAndFeatures _docIdAndFeatures;

    FileHandle();

    ~FileHandle();

    void
    open(const vespalib::stringref &dir,
         const SchemaUtil::IndexIterator &index,
         uint32_t docIdLimit, uint64_t numWordIds,
         const TuneFileSeqWrite &tuneFileWrite,
         const FileHeaderContext &fileHeaderContext);

    void
    close();
};


}

inline IndexBuilder::FieldHandle &
IndexBuilder::getIndexFieldHandle(uint32_t fieldId)
{
    return _fields[fieldId];
}


class IndexBuilder::FieldHandle
{
public:
    FieldHandle(const Schema &schema,
                uint32_t fieldId,
                IndexBuilder *ib);

    ~FieldHandle();

    static uint32_t
    noDocRef()
    {
        return std::numeric_limits<uint32_t>::max();
    }

    static uint32_t
    noElRef()
    {
        return std::numeric_limits<uint32_t>::max();
    }

    class FHWordDocFieldFeatures
    {
    public:
        uint32_t _docId;
        uint32_t _numElements;

        FHWordDocFieldFeatures(uint32_t docId)
            : _docId(docId),
              _numElements(0u)
        {
        }

        uint32_t
        getDocId() const
        {
            return _docId;
        }

        uint32_t
        getNumElements() const
        {
            return _numElements;
        }

        void
        incNumElements()
        {
            ++_numElements;
        }
    };

    class FHWordDocElementFeatures
        : public WordDocElementFeatures
    {
    public:
        uint32_t _docRef;

        FHWordDocElementFeatures(uint32_t elementId,
                                 int32_t weight,
                                 uint32_t elementLen,
                                 uint32_t docRef)
            : WordDocElementFeatures(elementId),
              _docRef(docRef)
        {
            setWeight(weight);
            setElementLen(elementLen);
        }
    };

    class FHWordDocElementWordPosFeatures
        : public WordDocElementWordPosFeatures
    {
    public:
        uint32_t _elementRef;

        FHWordDocElementWordPosFeatures(
                const WordDocElementWordPosFeatures &features,
                uint32_t elementRef)
            : WordDocElementWordPosFeatures(features),
              _elementRef(elementRef)
        {
        }
    };

    typedef vespalib::Array<FHWordDocFieldFeatures>          FHWordDocFieldFeaturesVector;
    typedef vespalib::Array<FHWordDocElementFeatures>        FHWordDocElementFeaturesVector;
    typedef vespalib::Array<FHWordDocElementWordPosFeatures> FHWordDocElementWordPosFeaturesVector;

    FHWordDocFieldFeaturesVector          _wdff;
    FHWordDocElementFeaturesVector        _wdfef;
    FHWordDocElementWordPosFeaturesVector _wdfepf;

    uint32_t _docRef;
    uint32_t _elRef;
    bool _valid;
    const Schema *_schema;  // Ptr to allow being std::vector member
    uint32_t _fieldId;
    IndexBuilder *_ib;  // Ptr to allow being std::vector member

    uint32_t _lowestOKElementId;
    uint32_t _lowestOKWordPos;

    FileHandle _files;

    void
    startWord(const vespalib::stringref &word);

    void
    endWord();

    void
    startDocument(uint32_t docId);

    void
    endDocument();

    void
    startElement(uint32_t elementId,
                 int32_t weight,
                 uint32_t elementLen);

    void
    endElement();

    void
    addOcc(const WordDocElementWordPosFeatures &features);

    void
    setValid()
    {
        _valid = true;
    }

    bool
    getValid() const
    {
        return _valid;
    }

    const Schema::IndexField &
    getSchemaField();

    const vespalib::string &
    getName();

    vespalib::string
    getDir();

    void
    open(uint32_t docIdLimit, uint64_t numWordIds,
         const TuneFileSeqWrite &tuneFileWrite,
         const FileHeaderContext &fileHeaderContext);

    void
    close();

    uint32_t
    getIndexId() const
    {
        return _fieldId;
    }
};


namespace {

class SingleIterator
{
public:
    typedef IndexBuilder::FieldHandle FH;
    FH::FHWordDocFieldFeaturesVector::const_iterator _dFeatures;
    FH::FHWordDocFieldFeaturesVector::const_iterator _dFeaturesE;
    FH::FHWordDocElementFeaturesVector::const_iterator _elFeatures;
    FH::FHWordDocElementWordPosFeaturesVector::const_iterator _pFeatures;
    uint32_t _docId;
    uint32_t _localFieldId;

    SingleIterator(FH &fieldHandle, uint32_t localFieldId);

    void
    appendFeatures(DocIdAndFeatures &features);

    bool
    isValid() const
    {
        return _dFeatures != _dFeaturesE;
    }

    bool
    operator<(const SingleIterator &rhs) const
    {
        if (_docId != rhs._docId)
            return _docId < rhs._docId;
        return _localFieldId < rhs._localFieldId;
    }
};


}


FileHandle::FileHandle()
    : _fieldWriter(NULL),
      _docIdAndFeatures()
{
}


FileHandle::~FileHandle()
{
    delete _fieldWriter;
}


void
FileHandle::open(const vespalib::stringref &dir,
                 const SchemaUtil::IndexIterator &index,
                 uint32_t docIdLimit, uint64_t numWordIds,
                 const TuneFileSeqWrite &tuneFileWrite,
                 const FileHeaderContext &fileHeaderContext)
{
    assert(_fieldWriter == NULL);

    _fieldWriter = new FieldWriter(docIdLimit, numWordIds);

    if (!_fieldWriter->open(dir + "/", 64, 262144u, false,
                            index.getSchema(), index.getIndex(),
                            tuneFileWrite, fileHeaderContext)) {
        LOG(error, "Could not open term writer %s for write (%s)",
            dir.c_str(), getLastErrorString().c_str());
        LOG_ABORT("should not be reached");
    }
}


void
FileHandle::close()
{
    bool ret = true;
    if (_fieldWriter != NULL) {
        bool closeRes = _fieldWriter->close();
        delete _fieldWriter;
        _fieldWriter = NULL;
        if (!closeRes) {
            LOG(error,
                "Could not close term writer");
            ret = false;
        }
    }
    assert(ret);
    (void) ret;
}


IndexBuilder::FieldHandle::FieldHandle(const Schema &schema,
                                       uint32_t fieldId,
                                       IndexBuilder *ib)
    : _wdff(),
      _wdfef(),
      _wdfepf(),
      _docRef(noDocRef()),
      _elRef(noElRef()),
      _valid(false),
      _schema(&schema),
      _fieldId(fieldId),
      _ib(ib),
      _lowestOKElementId(0u),
      _lowestOKWordPos(0u),
      _files()
{
}


IndexBuilder::FieldHandle::~FieldHandle()
{
}


void
IndexBuilder::FieldHandle::startWord(const vespalib::stringref &word)
{
    assert(_valid);
    _files._fieldWriter->newWord(word);
}


void
IndexBuilder::FieldHandle::endWord()
{
    DocIdAndFeatures &features = _files._docIdAndFeatures;
    SingleIterator si(*this, 0u);
    for (; si.isValid();) {
        features.clear(si._docId);
        si.appendFeatures(features);
        _files._fieldWriter->add(features);
    }
    assert(si._elFeatures == _wdfef.end());
    assert(si._pFeatures == _wdfepf.end());
    _wdff.clear();
    _wdfef.clear();
    _wdfepf.clear();
    _docRef = noDocRef();
    _elRef = noElRef();
}


void
IndexBuilder::FieldHandle::startDocument(uint32_t docId)
{
    assert(_docRef == noDocRef());
    assert(_wdff.empty() || _wdff.back().getDocId() < docId);
    _wdff.push_back(FHWordDocFieldFeatures(docId));
    _docRef = _wdff.size() - 1;
    _lowestOKElementId = 0u;
}


void
IndexBuilder::FieldHandle::endDocument()
{
    assert(_docRef != noDocRef());
    assert(_elRef == noElRef());
    FHWordDocFieldFeatures &ff = _wdff[_docRef];
    assert(ff.getNumElements() > 0);
    (void) ff;
    _docRef = noDocRef();
}


void
IndexBuilder::FieldHandle::
startElement(uint32_t elementId,
             int32_t weight,
             uint32_t elementLen)
{
    assert(_docRef != noDocRef());
    assert(_elRef == noElRef());
    assert(elementId >= _lowestOKElementId);

    FHWordDocFieldFeatures &ff = _wdff[_docRef];
    _wdfef.push_back(
            FHWordDocElementFeatures(elementId,
                                     weight,
                                     elementLen,
                                     _docRef));
    ff.incNumElements();
    _elRef = _wdfef.size() - 1;
    _lowestOKWordPos = 0u;
}


void
IndexBuilder::FieldHandle::endElement()
{
    assert(_elRef != noElRef());
    FHWordDocElementFeatures &ef = _wdfef[_elRef];
    assert(ef.getNumOccs() > 0);
    _elRef = noElRef();
    _lowestOKElementId = ef.getElementId() + 1;
}


void
IndexBuilder::FieldHandle::
addOcc(const WordDocElementWordPosFeatures &features)
{
    assert(_elRef != noElRef());
    FHWordDocElementFeatures &ef = _wdfef[_elRef];
    uint32_t wordPos = features.getWordPos();
    assert(wordPos < ef.getElementLen());
    assert(wordPos >= _lowestOKWordPos);
    _lowestOKWordPos = wordPos;
    _wdfepf.push_back(
            FHWordDocElementWordPosFeatures(features,
                                            _elRef));
    ef.incNumOccs();
}


const Schema::IndexField &
IndexBuilder::FieldHandle::getSchemaField()
{
    return _schema->getIndexField(_fieldId);
}


const vespalib::string &
IndexBuilder::FieldHandle::getName()
{
    return getSchemaField().getName();

}


vespalib::string
IndexBuilder::FieldHandle::getDir()
{
    return _ib->appendToPrefix(getName());
}


void
IndexBuilder::FieldHandle::open(uint32_t docIdLimit, uint64_t numWordIds,
                                const TuneFileSeqWrite &tuneFileWrite,
                                const FileHeaderContext &fileHeaderContext)
{
    _files.open(getDir(),
                SchemaUtil::IndexIterator(*_schema, getIndexId()),
                docIdLimit, numWordIds, tuneFileWrite, fileHeaderContext);
}


void
IndexBuilder::FieldHandle::close()
{
    _files.close();
}


SingleIterator::SingleIterator(FH &fieldHandle, uint32_t localFieldId)
    : _dFeatures(fieldHandle._wdff.begin()),
      _dFeaturesE(fieldHandle._wdff.end()),
      _elFeatures(fieldHandle._wdfef.begin()),
      _pFeatures(fieldHandle._wdfepf.begin()),
      _docId(_dFeatures->getDocId()),
      _localFieldId(localFieldId)
{
}


void
SingleIterator::appendFeatures(DocIdAndFeatures &features)
{
    uint32_t elCount = _dFeatures->getNumElements();
    for (uint32_t elId = 0; elId < elCount; ++elId, ++_elFeatures) {
        features._elements.push_back(*_elFeatures);
        features._elements.back().setNumOccs(0);
        uint32_t posCount = _elFeatures->getNumOccs();
        uint32_t lastWordPos = noWordPos();
        for (uint32_t posId = 0; posId < posCount; ++posId, ++_pFeatures) {
            uint32_t wordPos = _pFeatures->getWordPos();
            if (wordPos != lastWordPos) {
                lastWordPos = wordPos;
                features._elements.back().incNumOccs();
                features._wordPositions.push_back(*_pFeatures);
            }
        }
    }
    ++_dFeatures;
    if (_dFeatures != _dFeaturesE)
        _docId = _dFeatures->getDocId();
}


IndexBuilder::IndexBuilder(const Schema &schema)
    : index::IndexBuilder(schema),
      _currentField(NULL),
      _curDocId(noDocId()),
      _lowestOKDocId(1u),
      _curWord(),
      _inWord(false),
      _lowestOKFieldId(0u),
      _fields(),
      _prefix(),
      _docIdLimit(0u),
      _numWordIds(0u),
      _schema(schema)
{
    // TODO: Filter for text indexes
    for (uint32_t i = 0, ie = schema.getNumIndexFields(); i < ie; ++i) {
        const Schema::IndexField &iField = schema.getIndexField(i);
        FieldHandle fh(schema, i, this);
        // Only know how to handle string index for now.
        if (iField.getDataType() == DataType::STRING)
            fh.setValid();
        _fields.push_back(fh);
    }
}


IndexBuilder::~IndexBuilder()
{
}


void
IndexBuilder::startWord(const vespalib::stringref &word)
{
    assert(_currentField != nullptr);
    assert(!_inWord);
    // TODO: Check sort order
    _curWord = word;
    _inWord = true;
    _currentField->startWord(word);
}


void
IndexBuilder::endWord()
{
    assert(_inWord);
    assert(_currentField != NULL);
    _currentField->endWord();
    _inWord = false;
    _lowestOKDocId = 1u;
}


void
IndexBuilder::startDocument(uint32_t docId)
{
    assert(_curDocId == noDocId());
    assert(docId >= _lowestOKDocId);
    assert(docId < _docIdLimit);
    assert(_currentField != NULL);
    _curDocId = docId;
    assert(_curDocId != noDocId());
    _currentField->startDocument(docId);
}


void
IndexBuilder::endDocument()
{
    assert(_curDocId != noDocId());
    assert(_currentField != NULL);
    _currentField->endDocument();
    _lowestOKDocId = _curDocId + 1;
    _curDocId = noDocId();
}


void
IndexBuilder::startField(uint32_t fieldId)
{
    assert(_curDocId == noDocId());
    assert(_currentField == NULL);
    assert(fieldId < _fields.size());
    assert(fieldId >= _lowestOKFieldId);
    _currentField = &_fields[fieldId];
    assert(_currentField != NULL);
}


void
IndexBuilder::endField()
{
    assert(_curDocId == noDocId());
    assert(!_inWord);
    assert(_currentField != NULL);
    _lowestOKFieldId = _currentField->_fieldId + 1;
    _currentField = NULL;
}


void
IndexBuilder::startElement(uint32_t elementId,
                           int32_t weight,
                           uint32_t elementLen)
{
    assert(_currentField != NULL);
    _currentField->startElement(elementId, weight, elementLen);
}


void
IndexBuilder::endElement()
{
    assert(_currentField != NULL);
    _currentField->endElement();
}


void
IndexBuilder::addOcc(const WordDocElementWordPosFeatures &features)
{
    assert(_currentField != NULL);
    _currentField->addOcc(features);
}


void
IndexBuilder::setPrefix(const vespalib::stringref &prefix)
{
    _prefix = prefix;
}


vespalib::string
IndexBuilder::appendToPrefix(const vespalib::stringref &name)
{
    if (_prefix.empty())
        return name;
    return _prefix + "/" + name;
}


void
IndexBuilder::open(uint32_t docIdLimit, uint64_t numWordIds,
                   const TuneFileIndexing &tuneFileIndexing,
                   const FileHeaderContext &fileHeaderContext)
{
    std::vector<uint32_t> indexes;

    _docIdLimit = docIdLimit;
    _numWordIds = numWordIds;
    if (!_prefix.empty()) {
        vespalib::mkdir(_prefix, false);
    }
    // TODO: Filter for text indexes
    for (FieldHandle & fh : _fields) {
        if (!fh.getValid())
            continue;
        vespalib::mkdir(fh.getDir(), false);
        fh.open(docIdLimit, numWordIds, tuneFileIndexing._write,
                 fileHeaderContext);
        indexes.push_back(fh.getIndexId());
    }
    vespalib::string schemaFile = appendToPrefix("schema.txt");
    if (!_schema.saveToFile(schemaFile)) {
        LOG(error, "Cannot save schema to \"%s\"", schemaFile.c_str());
        LOG_ABORT("should not be reached");
    }
}


void
IndexBuilder::close()
{
    // TODO: Filter for text indexes
    for (FieldHandle & fh : _fields) {
        if (fh.getValid()) {
            fh.close();
        }
    }
    if (!docsummary::DocumentSummary::writeDocIdLimit(_prefix, _docIdLimit)) {
        LOG(error, "Could not write docsum count in dir %s: %s",
            _prefix.c_str(), getLastErrorString().c_str());
        LOG_ABORT("should not be reached");
    }
}


} // namespace diskindex

} // namespace search
