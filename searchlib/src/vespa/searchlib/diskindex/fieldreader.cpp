// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldreader.h"
#include "zcposocc.h"
#include "extposocc.h"
#include "pagedict4file.h"
#include <vespa/vespalib/util/error.h>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.fieldreader");

#define NO_DOC static_cast<uint32_t>(-1)

namespace {

vespalib::string PosOccIdCooked = "PosOcc.3.Cooked";

}

using vespalib::getLastErrorString;
using search::index::Schema;
using search::index::SchemaUtil;
using search::bitcompression::PosOccFieldParams;
using search::bitcompression::PosOccFieldsParams;

namespace search::diskindex {

FieldReader::FieldReader()
    : _wordNum(noWordNumHigh()),
      _docIdAndFeatures(),
      _dictFile(),
      _oldposoccfile(),
      _wordNumMapper(),
      _docIdMapper(),
      _oldWordNum(noWordNumHigh()),
      _residue(0u),
      _docIdLimit(0u),
      _word()
{
}


FieldReader::~FieldReader() = default;


void
FieldReader::readCounts()
{
    PostingListCounts counts;
    _dictFile->readWord(_word, _oldWordNum, counts);
    _oldposoccfile->readCounts(counts);
    if (_oldWordNum != noWordNumHigh()) {
        _wordNum = _wordNumMapper.map(_oldWordNum);
        assert(_wordNum != noWordNum());
        assert(_wordNum != noWordNumHigh());
        _residue = counts._numDocs;
    } else
        _wordNum = _oldWordNum;
}


void
FieldReader::readDocIdAndFeatures()
{
    _oldposoccfile->readDocIdAndFeatures(_docIdAndFeatures);
    _docIdAndFeatures.set_doc_id(_docIdMapper.mapDocId(_docIdAndFeatures.doc_id()));
}


void
FieldReader::read()
{
    for (;;) {
        while (_residue == 0) {
            readCounts();
            if (_wordNum == noWordNumHigh()) {
                assert(_residue == 0);
                _docIdAndFeatures.set_doc_id(NO_DOC);
                return;
            }
        }
        --_residue;
        readDocIdAndFeatures();
        if (_docIdAndFeatures.doc_id() != NO_DOC) {
            return;
        }
    }
}


bool
FieldReader::allowRawFeatures()
{
    return true;
}


void
FieldReader::setup(const WordNumMapping &wordNumMapping,
                   const DocIdMapping &docIdMapping)
{
    _wordNumMapper.setup(wordNumMapping);
    _docIdMapper.setup(docIdMapping);
}


bool
FieldReader::open(const vespalib::string &prefix,
                  const TuneFileSeqRead &tuneFileRead)
{
    vespalib::string name = prefix + "posocc.dat.compressed";
    FastOS_StatInfo statInfo;
    bool statres;

    bool dynamicKPosOccFormat = false;  // Will autodetect anyway
    statres = FastOS_File::Stat(name.c_str(), &statInfo);
    if (!statres) {
        LOG(error,
            "Could not stat compressed posocc file %s: %s",
            name.c_str(), getLastErrorString().c_str());
        return false;
    }

    _dictFile = std::make_unique<PageDict4FileSeqRead>();
    PostingListParams featureParams;
    _oldposoccfile.reset(makePosOccRead(name,
                                        _dictFile.get(),
                                        dynamicKPosOccFormat,
                                        featureParams,
                                        tuneFileRead));
    vespalib::string cname = prefix + "dictionary";

    if (!_dictFile->open(cname, tuneFileRead)) {
        LOG(error,
            "Could not open posocc count file %s for read",
            cname.c_str());
        return false;
    }

    // open posocc.dat
    if (!_oldposoccfile->open(name, tuneFileRead)) {
        LOG(error,
            "Could not open posocc file %s for read",
            name.c_str());
        return false;
    }
    _oldWordNum = noWordNum();
    _wordNum = _oldWordNum;
    PostingListParams params;
    _oldposoccfile->getParams(params);
    params.get("docIdLimit", _docIdLimit);
    return true;
}

bool
FieldReader::close()
{
    bool ret = true;

    if (_oldposoccfile) {
        bool closeRes = _oldposoccfile->close();
        if (!closeRes) {
            LOG(error,
                "Could not close posocc file for read");
            ret = false;
        }
        _oldposoccfile.reset();
    }
    if (_dictFile) {
        bool closeRes = _dictFile->close();
        if (!closeRes) {
            LOG(error,
                "Could not close posocc file for read");
            ret = false;
        }
        _dictFile.reset();
    }

    return ret;
}


void
FieldReader::setFeatureParams(const PostingListParams &params)
{
    _oldposoccfile->setFeatureParams(params);
}


void
FieldReader::getFeatureParams(PostingListParams &params)
{
    _oldposoccfile->getFeatureParams(params);
}


std::unique_ptr<FieldReader>
FieldReader::allocFieldReader(const SchemaUtil::IndexIterator &index,
                              const Schema &oldSchema)
{
    assert(index.isValid());
    if (index.hasMatchingOldFields(oldSchema, false)) {
        return std::make_unique<FieldReader>();      // The common case
    }
    if (!index.hasOldFields(oldSchema, false)) {
        return std::make_unique<FieldReaderEmpty>(index); // drop data
    }
    // field exists in old schema with different collection type setting
    return std::make_unique<FieldReaderStripInfo>(index);   // degraded
}


FieldReaderEmpty::FieldReaderEmpty(const IndexIterator &index)
    : _index(index)
{
}


bool
FieldReaderEmpty::open(const vespalib::string &prefix,
                       const TuneFileSeqRead &tuneFileRead)
{
    (void) prefix;
    (void) tuneFileRead;
    return true;
}


void
FieldReaderEmpty::getFeatureParams(PostingListParams &params)
{
    PosOccFieldsParams fieldsParams;
    fieldsParams.setSchemaParams(_index.getSchema(), _index.getIndex());
    params.clear();
    fieldsParams.getParams(params);
}


FieldReaderStripInfo::FieldReaderStripInfo(const IndexIterator &index)
    : _hasElements(false),
      _hasElementWeights(false)
{
    PosOccFieldsParams fieldsParams;
    fieldsParams.setSchemaParams(index.getSchema(), index.getIndex());
    assert(fieldsParams.getNumFields() > 0);
    const PosOccFieldParams &fieldParams = fieldsParams.getFieldParams()[0];
    _hasElements = fieldParams._hasElements;
    _hasElementWeights = fieldParams._hasElementWeights;
}


bool
FieldReaderStripInfo::allowRawFeatures()
{
    return false;
}


void
FieldReaderStripInfo::read()
{
    typedef search::index::WordDocElementFeatures Element;

    for (;;) {
        FieldReader::read();
        DocIdAndFeatures &features = _docIdAndFeatures;
        if (_wordNum == noWordNumHigh()) {
            return;
        }
        assert(!features.has_raw_data());
        uint32_t numElements = features.elements().size();
        assert(numElements > 0);
        std::vector<Element>::iterator element =
            features.elements().begin();
        if (_hasElements) {
            if (!_hasElementWeights) {
                for (uint32_t elementDone = 0; elementDone < numElements; ++elementDone, ++element) {
                    element->setWeight(1);
                }
                assert(element == features.elements().end());
            }
        } else {
            if (element->getElementId() != 0) {
                continue;   // Drop this entry, try to read new entry
            }
            element->setWeight(1);
            features.word_positions().resize(element->getNumOccs());
            if (numElements > 1) {
                features.elements().resize(1);
            }
        }
        break;
    }
}


void
FieldReaderStripInfo::getFeatureParams(PostingListParams &params)
{
    FieldReader::getFeatureParams(params);
    vespalib::string paramsPrefix = PosOccFieldParams::getParamsPrefix(0);
    vespalib::string collStr = paramsPrefix + ".collectionType";
    if (_hasElements) {
        if (_hasElementWeights) {
            params.setStr(collStr, "weightedSet");
        } else {
            params.setStr(collStr, "array");
        }
    } else
        params.setStr(collStr, "single");
    params.erase("encoding");
}

}
