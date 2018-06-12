// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compression.h"
#include "posocccompression.h"
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/data/fileheader.h>

#include <vespa/log/log.h>
LOG_SETUP(".posocccompression");

using search::index::DocIdAndFeatures;
using search::index::WordDocElementFeatures;
using search::index::WordDocElementWordPosFeatures;
using search::index::PostingListParams;
using search::index::SchemaUtil;
using search::index::Schema;
using search::fef::TermFieldMatchData;
using vespalib::GenericHeader;
using namespace search::index;

namespace {

vespalib::string PosOccId = "PosOcc.3";
vespalib::string PosOccIdCooked = "PosOcc.3.Cooked";

}

namespace {

vespalib::string EG64PosOccId = "EG64PosOcc.3"; // Dynamic k values
vespalib::string EG64PosOccId2 = "EG64PosOcc.2";    // Fixed k values

}

namespace search {

namespace bitcompression {


PosOccFieldParams::PosOccFieldParams()
    : _elemLenK(0),
      _hasElements(false),
      _hasElementWeights(false),
      _avgElemLen(512),
      _collectionType(SINGLE),
      _name()
{ }


bool
PosOccFieldParams::operator==(const PosOccFieldParams &rhs) const
{
    return _collectionType == rhs._collectionType &&
              _avgElemLen == rhs._avgElemLen &&
                     _name == rhs._name;
}


vespalib::string
PosOccFieldParams::getParamsPrefix(uint32_t idx)
{
    vespalib::asciistream paramsPrefix;
    paramsPrefix << "fieldParams.";
    paramsPrefix << idx;
    return paramsPrefix.str();
}


void
PosOccFieldParams::getParams(PostingListParams &params, uint32_t idx) const
{
    vespalib::string paramsPrefix = getParamsPrefix(idx);
    vespalib::string collStr = paramsPrefix + ".collectionType";
    vespalib::string avgElemLenStr = paramsPrefix + ".avgElemLen";
    vespalib::string nameStr = paramsPrefix + ".name";

    switch (_collectionType) {
    case SINGLE:
        params.setStr(collStr, "single");
        break;
    case ARRAY:
        params.setStr(collStr, "array");
        break;
    case WEIGHTEDSET:
        params.setStr(collStr, "weightedSet");
        break;
    }
    params.set(avgElemLenStr, _avgElemLen);
    params.setStr(nameStr, _name);
}


void
PosOccFieldParams::setParams(const PostingListParams &params, uint32_t idx)
{
    vespalib::string paramsPrefix = getParamsPrefix(idx);
    vespalib::string collStr = paramsPrefix + ".collectionType";
    vespalib::string avgElemLenStr = paramsPrefix + ".avgElemLen";
    vespalib::string nameStr = paramsPrefix + ".name";

    if (params.isSet(collStr)) {
        vespalib::string collVal = params.getStr(collStr);
        if (collVal == "single") {
            _collectionType = SINGLE;
            _hasElements = false;
            _hasElementWeights = false;
        } else if (collVal == "array") {
            _collectionType = ARRAY;
            _hasElements = true;
            _hasElementWeights = false;
        } else if (collVal == "weightedSet") {
            _collectionType = WEIGHTEDSET;
            _hasElements = true;
            _hasElementWeights = true;
        }
    }
    params.get(avgElemLenStr, _avgElemLen);
    if (params.isSet(nameStr))
        _name = params.getStr(nameStr);
}


void
PosOccFieldParams::setSchemaParams(const Schema &schema, uint32_t fieldId)
{
    assert(fieldId < schema.getNumIndexFields());
    const Schema::IndexField &field = schema.getIndexField(fieldId);
    switch (field.getCollectionType()) {
    case schema::CollectionType::SINGLE:
        _collectionType = SINGLE;
        _hasElements = false;
        _hasElementWeights = false;
        break;
    case schema::CollectionType::ARRAY:
        _collectionType = ARRAY;
        _hasElements = true;
        _hasElementWeights = false;
        break;
    case schema::CollectionType::WEIGHTEDSET:
        _collectionType = WEIGHTEDSET;
        _hasElements = true;
        _hasElementWeights = true;
        break;
    default:
        LOG(error, "Bad collection type");
        LOG_ABORT("should not be reached");
    }
    _avgElemLen = field.getAvgElemLen();
    _name = field.getName();
}


void
PosOccFieldParams::readHeader(const vespalib::GenericHeader &header,
                              const vespalib::string &prefix)
{
    vespalib::string nameKey(prefix + "fieldName");
    vespalib::string collKey(prefix + "collectionType");
    vespalib::string avgElemLenKey(prefix + "avgElemLen");
    _name = header.getTag(nameKey).asString();
    Schema::CollectionType ct = schema::collectionTypeFromName(header.getTag(collKey).asString());
    switch (ct) {
    case schema::CollectionType::SINGLE:
        _collectionType = SINGLE;
        _hasElements = false;
        _hasElementWeights = false;
        break;
    case schema::CollectionType::ARRAY:
        _collectionType = ARRAY;
        _hasElements = true;
        _hasElementWeights = false;
        break;
    case schema::CollectionType::WEIGHTEDSET:
        _collectionType = WEIGHTEDSET;
        _hasElements = true;
        _hasElementWeights = true;
        break;
    default:
        LOG(error, "Bad collection type when reading field param in header");
        LOG_ABORT("should not be reached");
    }
    _avgElemLen = header.getTag(avgElemLenKey).asInteger();
}


void
PosOccFieldParams::writeHeader(vespalib::GenericHeader &header,
                               const vespalib::string &prefix) const
{
    vespalib::string nameKey(prefix + "fieldName");
    vespalib::string collKey(prefix + "collectionType");
    vespalib::string avgElemLenKey(prefix + "avgElemLen");
    header.putTag(GenericHeader::Tag(nameKey, _name));
    Schema::CollectionType ct(schema::CollectionType::SINGLE);
    switch (_collectionType) {
    case SINGLE:
        ct = schema::CollectionType::SINGLE;
        break;
    case ARRAY:
        ct = schema::CollectionType::ARRAY;
        break;
    case WEIGHTEDSET:
        ct = schema::CollectionType::WEIGHTEDSET;
        break;
    default:
        LOG(error,
            "Bad collection type when writing field param in header");
        LOG_ABORT("should not be reached");
    }
    header.putTag(GenericHeader::Tag(collKey, schema::getTypeName(ct)));
    header.putTag(GenericHeader::Tag(avgElemLenKey, _avgElemLen));
}


PosOccFieldsParams::PosOccFieldsParams()
    : _numFields(0u),
      _fieldParams(NULL),
      _params()
{
}

PosOccFieldsParams::PosOccFieldsParams(const PosOccFieldsParams &rhs)
    : _numFields(0u),
      _fieldParams(NULL),
      _params(rhs._params)
{
    cacheParamsRef();
}

PosOccFieldsParams &
PosOccFieldsParams::operator=(const PosOccFieldsParams &rhs)
{
    assertCachedParamsRef();
    _params = rhs._params;
    cacheParamsRef();
    return *this;
}


bool
PosOccFieldsParams::operator==(const PosOccFieldsParams &rhs) const
{
    return _params == rhs._params;
}


void
PosOccFieldsParams::getParams(PostingListParams &params) const
{
    assertCachedParamsRef();
    assert(_numFields == 1u); // Only single field for now
    params.set("numFields", _numFields);
    // Single posting file index format will have multiple fields in file
    for (uint32_t field = 0; field < _numFields; ++field)
        _fieldParams[field].getParams(params, field);
}


void
PosOccFieldsParams::setParams(const PostingListParams &params)
{
    assertCachedParamsRef();
    uint32_t numFields = _numFields;
    params.get("numFields", numFields);
    assert(numFields == 1u);
    _params.resize(numFields);
    cacheParamsRef();
    // Single posting file index format will have multiple fields in file
    for (uint32_t field = 0; field < numFields; ++field)
        _params[field].setParams(params, field);
}


void
PosOccFieldsParams::setSchemaParams(const Schema &schema,
                                    const uint32_t indexId)
{
    assertCachedParamsRef();
    SchemaUtil::IndexIterator i(schema, indexId);
    assert(i.isValid());
    _params.resize(1u);
    cacheParamsRef();
    const Schema::IndexField &field = schema.getIndexField(indexId);
    if (!SchemaUtil::validateIndexField(field))
        LOG_ABORT("should not be reached");
    _params[0].setSchemaParams(schema, indexId);
}


void
PosOccFieldsParams::readHeader(const vespalib::GenericHeader &header,
               const vespalib::string &prefix)
{
    vespalib::string numFieldsKey(prefix + "numFields");
    assertCachedParamsRef();
    uint32_t numFields = header.getTag(numFieldsKey).asInteger();
    assert(numFields == 1u);
    _params.resize(numFields);
    cacheParamsRef();
    // Single posting file index format will have multiple fields in file
    for (uint32_t field = 0; field < numFields; ++field) {
        vespalib::asciistream as;
        as << prefix << "field[" << field << "].";
        vespalib::string subPrefix(as.str());
        _params[field].readHeader(header, subPrefix);
    }
}


void
PosOccFieldsParams::writeHeader(vespalib::GenericHeader &header,
                                const vespalib::string &prefix) const
{
    vespalib::string numFieldsKey(prefix + "numFields");
    assertCachedParamsRef();
    assert(_numFields == 1u);
    header.putTag(GenericHeader::Tag(numFieldsKey, _numFields));
    // Single posting file index format will have multiple fields in file
    for (uint32_t field = 0; field < _numFields; ++field) {
        vespalib::asciistream as;
        as << prefix << "field[" << field << "].";
        vespalib::string subPrefix(as.str());
        _params[field].writeHeader(header, subPrefix);
    }
}


template <bool bigEndian>
void
EG2PosOccDecodeContext<bigEndian>::
readHeader(const vespalib::GenericHeader &header,
           const vespalib::string &prefix)
{
    const_cast<PosOccFieldsParams *>(_fieldsParams)->readHeader(header,
            prefix);
}


template <bool bigEndian>
void
EG2PosOccDecodeContext<bigEndian>::
readFeatures(search::index::DocIdAndFeatures &features)
{
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;
    const uint64_t *valE = _valE;

    features.clearFeatures((oPreRead == 0) ? 0 : 64 - oPreRead);
    features.setRaw(true);
    const uint64_t *rawFeatures =
        (oPreRead == 0) ? (oCompr - 1) : (oCompr - 2);
    uint64_t rawFeaturesStartBitPos =
        _fileReadBias + (reinterpret_cast<unsigned long>(oCompr) << 3) -
        oPreRead;

    const PosOccFieldParams &fieldParams =
        _fieldsParams->getFieldParams()[0];
    uint32_t numElements = 1;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMELEMENTS,
                                      EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    for (uint32_t elementDone = 0; elementDone < numElements;
         ++elementDone) {
        if (fieldParams._hasElements) {
            UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                        K_VALUE_POSOCC_ELEMENTID,
                                        EC);
            if (fieldParams._hasElementWeights) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                            K_VALUE_POSOCC_ELEMENTWEIGHT,
                                            EC);
            }
            if (__builtin_expect(oCompr >= valE, false)) {
                while (rawFeatures < oCompr) {
                    features._blob.push_back(*rawFeatures);
                    ++rawFeatures;
                }
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
                rawFeatures = oCompr;
            }
        }
        UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                    K_VALUE_POSOCC_ELEMENTLEN,
                                    EC);
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMPOSITIONS,
                                      EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        do {
            if (__builtin_expect(oCompr >= valE, false)) {
                while (rawFeatures < oCompr) {
                    features._blob.push_back(*rawFeatures);
                    ++rawFeatures;
                }
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
                rawFeatures = oCompr;
            }
            UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                        K_VALUE_POSOCC_FIRST_WORDPOS,
                                        EC);
        } while (0);
        for (uint32_t pos = 1; pos < numPositions; ++pos) {
            if (__builtin_expect(oCompr >= valE, false)) {
                while (rawFeatures < oCompr) {
                    features._blob.push_back(*rawFeatures);
                    ++rawFeatures;
                }
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
                rawFeatures = oCompr;
            }
            UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                        K_VALUE_POSOCC_DELTA_WORDPOS,
                                        EC);
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
    uint64_t rawFeaturesEndBitPos =
        _fileReadBias +
        (reinterpret_cast<unsigned long>(oCompr) << 3) -
        oPreRead;
    features._bitLength = rawFeaturesEndBitPos - rawFeaturesStartBitPos;
    while (rawFeatures < oCompr) {
        features._blob.push_back(*rawFeatures);
        ++rawFeatures;
    }
    if (__builtin_expect(oCompr >= valE, false)) {
        _readContext->readComprBuffer();
    }
}


template <bool bigEndian>
void
EG2PosOccDecodeContextCooked<bigEndian>::
readFeatures(search::index::DocIdAndFeatures &features)
{
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;
    const uint64_t *valE = _valE;

    features.clearFeatures();
    features.setRaw(false);

    const PosOccFieldParams &fieldParams = _fieldsParams->getFieldParams()[0];
    uint32_t numElements = 1;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMELEMENTS,
                                      EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    uint32_t elementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements;
         ++elementDone, ++elementId) {
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_ELEMENTID,
                                          EC);
            elementId += static_cast<uint32_t>(val64);
        }
        features._elements.
            push_back(WordDocElementFeatures(elementId));
        if (fieldParams._hasElementWeights) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_ELEMENTWEIGHT,
                                          EC);
            int32_t elementWeight = this->convertToSigned(val64);
            features._elements.back().setWeight(elementWeight);
        }
        if (__builtin_expect(oCompr >= valE, false)) {
            UC64_DECODECONTEXT_STORE(o, _);
            _readContext->readComprBuffer();
            valE = _valE;
            UC64_DECODECONTEXT_LOAD(o, _);
        }
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_ELEMENTLEN,
                                      EC);
        uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
        features._elements.back().setElementLen(elementLen);
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMPOSITIONS,
                                      EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        uint32_t wordPos = static_cast<uint32_t>(-1);
        do {
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_FIRST_WORDPOS,
                                          EC);
            wordPos = static_cast<uint32_t>(val64);
            features._elements.back().incNumOccs();
            features._wordPositions.push_back(
                    WordDocElementWordPosFeatures(wordPos));
        } while (0);
        for (uint32_t pos = 1; pos < numPositions; ++pos) {
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_DELTA_WORDPOS,
                                          EC);
            wordPos += 1 + static_cast<uint32_t>(val64);
            features._elements.back().incNumOccs();
            features._wordPositions.push_back(
                    WordDocElementWordPosFeatures(wordPos));
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
    if (__builtin_expect(oCompr >= valE, false))
        _readContext->readComprBuffer();
}


template <bool bigEndian>
void
EG2PosOccDecodeContext<bigEndian>::
skipFeatures(unsigned int count)
{
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;

    for (unsigned int i = count; i > 0; --i) {
        const PosOccFieldParams &fieldParams =
            _fieldsParams->getFieldParams()[0];
        uint32_t numElements = 1;
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_NUMELEMENTS,
                                          EC);
            numElements = static_cast<uint32_t>(val64) + 1;
        }
        for (uint32_t elementDone = 0; elementDone < numElements;
             ++elementDone) {
            if (fieldParams._hasElements) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                            K_VALUE_POSOCC_ELEMENTID,
                                            EC);
                if (fieldParams._hasElementWeights) {
                    UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                                K_VALUE_POSOCC_ELEMENTWEIGHT,
                                                EC);
                }
            }
            UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                        K_VALUE_POSOCC_ELEMENTLEN,
                                        EC);
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_NUMPOSITIONS,
                                          EC);
            uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

            UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                        K_VALUE_POSOCC_FIRST_WORDPOS,
                                        EC);
            for (uint32_t pos = 1; pos < numPositions; ++pos) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                            K_VALUE_POSOCC_DELTA_WORDPOS,
                                            EC);
            }
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
}


template <bool bigEndian>
void
EG2PosOccDecodeContext<bigEndian>::
unpackFeatures(const search::fef::TermFieldMatchDataArray &matchData,
               uint32_t docId)
{
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;

    const PosOccFieldParams &fieldParams =
        _fieldsParams->getFieldParams()[0];
    uint32_t numElements = 1;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMELEMENTS,
                                      EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    TermFieldMatchData *tfmd = matchData[0];
    tfmd->reset(docId);
    uint32_t elementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements;
         ++elementDone, ++elementId) {
        int32_t elementWeight = 1;
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_ELEMENTID,
                                          EC);
            elementId += static_cast<uint32_t>(val64);
            if (fieldParams._hasElementWeights) {
                UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                              K_VALUE_POSOCC_ELEMENTWEIGHT,
                                              EC);
                elementWeight = this->convertToSigned(val64);
            }
        }

        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_ELEMENTLEN,
                                      EC);
        uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMPOSITIONS,
                                      EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_FIRST_WORDPOS,
                                      EC);
        uint32_t wordPos = static_cast<uint32_t>(val64);
        {
            search::fef::TermFieldMatchDataPosition
                pos(elementId, wordPos, elementWeight, elementLen);
            tfmd->appendPosition(pos);
        }
        for (uint32_t wi = 1; wi < numPositions; ++wi) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_DELTA_WORDPOS,
                                          EC);
            wordPos += 1 + static_cast<uint32_t>(val64);
            {
                search::fef::TermFieldMatchDataPosition
                    pos(elementId, wordPos, elementWeight,
                        elementLen);
                tfmd->appendPosition(pos);
            }
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
}


template <bool bigEndian>
void
EG2PosOccDecodeContext<bigEndian>::
setParams(const PostingListParams &params)
{
    const_cast<PosOccFieldsParams *>(_fieldsParams)->setParams(params);
}


template <bool bigEndian>
void
EG2PosOccDecodeContext<bigEndian>::
getParams(PostingListParams &params) const
{
    params.clear();
    params.setStr("encoding", EG64PosOccId2);
    _fieldsParams->getParams(params);
}


template <bool bigEndian>
void
EG2PosOccDecodeContextCooked<bigEndian>::
getParams(PostingListParams &params) const
{
    ParentClass::getParams(params);
    params.setStr("cookedEncoding", PosOccIdCooked);
}


template <bool bigEndian>
void
EG2PosOccEncodeContext<bigEndian>::
readHeader(const vespalib::GenericHeader &header,
           const vespalib::string &prefix)
{
    const_cast<PosOccFieldsParams *>(_fieldsParams)->readHeader(header,
            prefix);
}


template <bool bigEndian>
const vespalib::string &
EG2PosOccDecodeContext<bigEndian>::getIdentifier() const
{
    return EG64PosOccId2;
}


template <bool bigEndian>
void
EG2PosOccEncodeContext<bigEndian>::
writeHeader(vespalib::GenericHeader &header,
            const vespalib::string &prefix) const
{
    _fieldsParams->writeHeader(header, prefix);
}


template <bool bigEndian>
const vespalib::string &
EG2PosOccEncodeContext<bigEndian>::getIdentifier() const
{
    return EG64PosOccId2;
}


template <bool bigEndian>
void
EG2PosOccEncodeContext<bigEndian>::
writeFeatures(const search::index::DocIdAndFeatures &features)
{
    if (features.getRaw()) {
        writeBits(&features._blob[0],
                  features._bitOffset, features._bitLength);
        return;
    }
    typedef WordDocElementFeatures Elements;
    typedef WordDocElementWordPosFeatures Positions;

    std::vector<Elements>::const_iterator element = features._elements.begin();

    std::vector<Positions>::const_iterator position =
        features._wordPositions.begin();

    const PosOccFieldParams &fieldParams =
        _fieldsParams->getFieldParams()[0];

    uint32_t numElements = features._elements.size();
    if (fieldParams._hasElements) {
        assert(numElements > 0u);
        encodeExpGolomb(numElements - 1,
                        K_VALUE_POSOCC_NUMELEMENTS);
    } else {
        assert(numElements == 1);
    }
    uint32_t minElementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements;
         ++elementDone, ++element) {
        if (fieldParams._hasElements) {
            uint32_t elementId = element->getElementId();
            assert(elementId >= minElementId);
            encodeExpGolomb(elementId - minElementId,
                            K_VALUE_POSOCC_ELEMENTID);
            minElementId = elementId + 1;
            if (fieldParams._hasElementWeights) {
                int32_t elementWeight = element->getWeight();
                encodeExpGolomb(this->convertToUnsigned(elementWeight),
                                K_VALUE_POSOCC_ELEMENTWEIGHT);
            }
            if (__builtin_expect(_valI >= _valE, false))
                _writeContext->writeComprBuffer(false);
        } else {
            uint32_t elementId = element->getElementId();
            assert(elementId == 0);
            (void) elementId;
        }

        encodeExpGolomb(element->getElementLen() - 1,
                        K_VALUE_POSOCC_ELEMENTLEN);
        uint32_t numPositions = element->getNumOccs();
        assert(numPositions > 0);
        encodeExpGolomb(numPositions - 1,
                        K_VALUE_POSOCC_NUMPOSITIONS);

        uint32_t wordPos = static_cast<uint32_t>(-1);
        do {
            uint32_t lastWordPos = wordPos;
            wordPos = position->getWordPos();
            encodeExpGolomb(wordPos - lastWordPos - 1,
                            K_VALUE_POSOCC_FIRST_WORDPOS);
            if (__builtin_expect(_valI >= _valE, false))
                _writeContext->writeComprBuffer(false);
            ++position;
        } while (0);
        uint32_t positionResidue = numPositions - 1;
        while (positionResidue > 0) {
            uint32_t lastWordPos = wordPos;
            wordPos = position->getWordPos();
            encodeExpGolomb(wordPos - lastWordPos - 1,
                            K_VALUE_POSOCC_DELTA_WORDPOS);
            if (__builtin_expect(_valI >= _valE, false))
                _writeContext->writeComprBuffer(false);
            ++position;
            --positionResidue;
        }
    }
}


template <bool bigEndian>
void
EG2PosOccEncodeContext<bigEndian>::
setParams(const PostingListParams &params)
{
    const_cast<PosOccFieldsParams *>(_fieldsParams)->setParams(params);
}


template <bool bigEndian>
void
EG2PosOccEncodeContext<bigEndian>::
getParams(PostingListParams &params) const
{
    params.clear();
    params.setStr("encoding", EG64PosOccId2);
    params.setStr("cookedEncoding", PosOccIdCooked);
    _fieldsParams->getParams(params);
}


template <bool bigEndian>
void
EGPosOccDecodeContext<bigEndian>::
readHeader(const vespalib::GenericHeader &header,
           const vespalib::string &prefix)
{
    ParentClass::readHeader(header, prefix);
}


template <bool bigEndian>
void
EGPosOccDecodeContext<bigEndian>::
readFeatures(search::index::DocIdAndFeatures &features)
{
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;
    const uint64_t *valE = _valE;

    features.clearFeatures((oPreRead == 0) ? 0 : 64 - oPreRead);
    features.setRaw(true);
    const uint64_t *rawFeatures =
        (oPreRead == 0) ? (oCompr - 1) : (oCompr - 2);
    uint64_t rawFeaturesStartBitPos =
        _fileReadBias + (reinterpret_cast<unsigned long>(oCompr) << 3) -
        oPreRead;

    const PosOccFieldParams &fieldParams =
        _fieldsParams->getFieldParams()[0];
    uint32_t elementLenK = EGPosOccEncodeContext<bigEndian>::
                           calcElementLenK(fieldParams._avgElemLen);
    uint32_t numElements = 1;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMELEMENTS,
                                      EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    for (uint32_t elementDone = 0; elementDone < numElements;
         ++elementDone) {
        if (fieldParams._hasElements) {
            UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                        K_VALUE_POSOCC_ELEMENTID,
                                        EC);
            if (fieldParams._hasElementWeights) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                            K_VALUE_POSOCC_ELEMENTWEIGHT,
                                            EC);
            }
            if (__builtin_expect(oCompr >= valE, false)) {
                while (rawFeatures < oCompr) {
                    features._blob.push_back(*rawFeatures);
                    ++rawFeatures;
                }
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
                rawFeatures = oCompr;
            }
        }
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      elementLenK,
                                      EC);
        uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMPOSITIONS,
                                      EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        uint32_t wordPosK = EGPosOccEncodeContext<bigEndian>::
                            calcWordPosK(numPositions, elementLen);

        for (uint32_t pos = 0; pos < numPositions; ++pos) {
            if (__builtin_expect(oCompr >= valE, false)) {
                while (rawFeatures < oCompr) {
                    features._blob.push_back(*rawFeatures);
                    ++rawFeatures;
                }
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
                rawFeatures = oCompr;
            }
            UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                        wordPosK,
                                        EC);
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
    uint64_t rawFeaturesEndBitPos =
        _fileReadBias +
        (reinterpret_cast<unsigned long>(oCompr) << 3) -
        oPreRead;
    features._bitLength = rawFeaturesEndBitPos - rawFeaturesStartBitPos;
    while (rawFeatures < oCompr) {
        features._blob.push_back(*rawFeatures);
        ++rawFeatures;
    }
    if (__builtin_expect(oCompr >= valE, false)) {
        _readContext->readComprBuffer();
    }
}


template <bool bigEndian>
void
EGPosOccDecodeContextCooked<bigEndian>::
readFeatures(search::index::DocIdAndFeatures &features)
{
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;
    const uint64_t *valE = _valE;

    features.clearFeatures();
    features.setRaw(false);

    const PosOccFieldParams &fieldParams =
        _fieldsParams->getFieldParams()[0];
    uint32_t elementLenK = EGPosOccEncodeContext<bigEndian>::
                           calcElementLenK(fieldParams._avgElemLen);
    uint32_t numElements = 1;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMELEMENTS,
                                      EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    uint32_t elementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements;
         ++elementDone, ++elementId) {
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_ELEMENTID,
                                          EC);
            elementId += static_cast<uint32_t>(val64);
        }
        features._elements.
            push_back(WordDocElementFeatures(elementId));
        if (fieldParams._hasElementWeights) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_ELEMENTWEIGHT,
                                          EC);
            int32_t elementWeight = this->convertToSigned(val64);
            features._elements.back().setWeight(elementWeight);
        }
        if (__builtin_expect(oCompr >= valE, false)) {
            UC64_DECODECONTEXT_STORE(o, _);
            _readContext->readComprBuffer();
            valE = _valE;
            UC64_DECODECONTEXT_LOAD(o, _);
        }
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      elementLenK,
                                      EC);
        uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
        features._elements.back().setElementLen(elementLen);
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMPOSITIONS,
                                      EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        features._bitLength = numPositions * 64;

        uint32_t wordPosK = EGPosOccEncodeContext<bigEndian>::
                            calcWordPosK(numPositions, elementLen);

        uint32_t wordPos = static_cast<uint32_t>(-1);
        for (uint32_t pos = 0; pos < numPositions; ++pos) {
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          wordPosK,
                                          EC);
            wordPos += 1 + static_cast<uint32_t>(val64);
            features._elements.back().incNumOccs();
            features._wordPositions.push_back(
                    WordDocElementWordPosFeatures(wordPos));
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
    if (__builtin_expect(oCompr >= valE, false))
        _readContext->readComprBuffer();
}


template <bool bigEndian>
void
EGPosOccDecodeContext<bigEndian>::
skipFeatures(unsigned int count)
{
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;

    for (unsigned int i = count; i > 0; --i) {
        const PosOccFieldParams &fieldParams =
            _fieldsParams->getFieldParams()[0];
        uint32_t elementLenK = EGPosOccEncodeContext<bigEndian>::
                               calcElementLenK(fieldParams._avgElemLen);
        uint32_t numElements = 1;
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_NUMELEMENTS,
                                          EC);
            numElements = static_cast<uint32_t>(val64) + 1;
        }
        for (uint32_t elementDone = 0; elementDone < numElements;
             ++elementDone) {
            if (fieldParams._hasElements) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                            K_VALUE_POSOCC_ELEMENTID,
                                            EC);
                if (fieldParams._hasElementWeights) {
                    UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                                K_VALUE_POSOCC_ELEMENTWEIGHT,
                                                EC);
                }
            }
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          elementLenK,
                                          EC);
            uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_NUMPOSITIONS,
                                          EC);
            uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

            uint32_t wordPosK = EGPosOccEncodeContext<bigEndian>::
                                calcWordPosK(numPositions, elementLen);

            for (uint32_t pos = 0; pos < numPositions; ++pos) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o,
                                            wordPosK,
                                            EC);
            }
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
}


template <bool bigEndian>
void
EGPosOccDecodeContext<bigEndian>::
unpackFeatures(const search::fef::TermFieldMatchDataArray &matchData,
               uint32_t docId)
{
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;

    const PosOccFieldParams &fieldParams =
        _fieldsParams->getFieldParams()[0];
    uint32_t elementLenK = EGPosOccEncodeContext<bigEndian>::
                           calcElementLenK(fieldParams._avgElemLen);
    uint32_t numElements = 1;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMELEMENTS,
                                      EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    TermFieldMatchData *tfmd = matchData[0];
    tfmd->reset(docId);
    uint32_t elementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements;
         ++elementDone, ++elementId) {
        int32_t elementWeight = 1;
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          K_VALUE_POSOCC_ELEMENTID,
                                          EC);
            elementId += static_cast<uint32_t>(val64);
            if (fieldParams._hasElementWeights) {
                UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                              K_VALUE_POSOCC_ELEMENTWEIGHT,
                                              EC);
                elementWeight = this->convertToSigned(val64);
            }
        }
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      elementLenK,
                                      EC);
        uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMPOSITIONS,
                                      EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        uint32_t wordPosK = EGPosOccEncodeContext<bigEndian>::
                            calcWordPosK(numPositions, elementLen);

        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      wordPosK,
                                      EC);
        uint32_t wordPos = static_cast<uint32_t>(val64);
        {
            search::fef::TermFieldMatchDataPosition
                pos(elementId, wordPos, elementWeight, elementLen);
            tfmd->appendPosition(pos);
        }
        for (uint32_t wi = 1; wi < numPositions; ++wi) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                          wordPosK,
                                          EC);
            wordPos += 1 + static_cast<uint32_t>(val64);
            {
                search::fef::TermFieldMatchDataPosition
                    pos(elementId, wordPos, elementWeight,
                        elementLen);
                tfmd->appendPosition(pos);
            }
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
}


template <bool bigEndian>
void
EGPosOccDecodeContext<bigEndian>::
setParams(const PostingListParams &params)
{
    ParentClass::setParams(params);
}


template <bool bigEndian>
void
EGPosOccDecodeContext<bigEndian>::
getParams(PostingListParams &params) const
{
    ParentClass::getParams(params);
    params.setStr("encoding", EG64PosOccId);
}


template <bool bigEndian>
void
EGPosOccDecodeContextCooked<bigEndian>::
getParams(PostingListParams &params) const
{
    ParentClass::getParams(params);
    params.setStr("cookedEncoding", PosOccIdCooked);
}


template <bool bigEndian>
void
EGPosOccEncodeContext<bigEndian>::
readHeader(const vespalib::GenericHeader &header,
           const vespalib::string &prefix)
{
    ParentClass::readHeader(header, prefix);
}


template <bool bigEndian>
const vespalib::string &
EGPosOccDecodeContext<bigEndian>::getIdentifier() const
{
    return EG64PosOccId;
}


template <bool bigEndian>
void
EGPosOccEncodeContext<bigEndian>::
writeHeader(vespalib::GenericHeader &header,
            const vespalib::string &prefix) const
{
    ParentClass::writeHeader(header, prefix);
}


template <bool bigEndian>
const vespalib::string &
EGPosOccEncodeContext<bigEndian>::getIdentifier() const
{
    return EG64PosOccId;
}


template <bool bigEndian>
void
EGPosOccEncodeContext<bigEndian>::
writeFeatures(const search::index::DocIdAndFeatures &features)
{
    if (features.getRaw()) {
        writeBits(&features._blob[0],
                  features._bitOffset, features._bitLength);
        return;
    }
    typedef WordDocElementFeatures Elements;
    typedef WordDocElementWordPosFeatures Positions;

    std::vector<Elements>::const_iterator element = features._elements.begin();

    std::vector<Positions>::const_iterator position =
        features._wordPositions.begin();
    const PosOccFieldParams &fieldParams =
        _fieldsParams->getFieldParams()[0];
    uint32_t elementLenK = calcElementLenK(fieldParams._avgElemLen);

    uint32_t numElements = features._elements.size();
    if (fieldParams._hasElements) {
        assert(numElements > 0u);
        encodeExpGolomb(numElements - 1,
                        K_VALUE_POSOCC_NUMELEMENTS);
    } else {
        assert(numElements == 1);
    }
    uint32_t minElementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements;
         ++elementDone, ++element) {
        if (fieldParams._hasElements) {
            uint32_t elementId = element->getElementId();
            assert(elementId >= minElementId);
            encodeExpGolomb(elementId - minElementId,
                            K_VALUE_POSOCC_ELEMENTID);
            minElementId = elementId + 1;
            if (fieldParams._hasElementWeights) {
                int32_t elementWeight = element->getWeight();
                encodeExpGolomb(this->convertToUnsigned(elementWeight),
                                K_VALUE_POSOCC_ELEMENTWEIGHT);
            }
            if (__builtin_expect(_valI >= _valE, false))
                _writeContext->writeComprBuffer(false);
        } else {
            uint32_t elementId = element->getElementId();
            assert(elementId == 0);
            (void) elementId;
        }
        uint32_t elementLen = element->getElementLen();
        encodeExpGolomb(elementLen - 1, elementLenK);
        uint32_t numPositions = element->getNumOccs();
        assert(numPositions > 0);
        encodeExpGolomb(numPositions - 1,
                        K_VALUE_POSOCC_NUMPOSITIONS);

        uint32_t wordPosK = calcWordPosK(numPositions, elementLen);
        uint32_t wordPos = static_cast<uint32_t>(-1);
        uint32_t positionResidue = numPositions;
        while (positionResidue > 0) {
            uint32_t lastWordPos = wordPos;
            wordPos = position->getWordPos();
            encodeExpGolomb(wordPos - lastWordPos - 1,
                            wordPosK);
            if (__builtin_expect(_valI >= _valE, false))
                _writeContext->writeComprBuffer(false);
            ++position;
            --positionResidue;
        }
    }
}



template <bool bigEndian>
void
EGPosOccEncodeContext<bigEndian>::
setParams(const PostingListParams &params)
{
    ParentClass::setParams(params);
}


template <bool bigEndian>
void
EGPosOccEncodeContext<bigEndian>::
getParams(PostingListParams &params) const
{
    ParentClass::getParams(params);
    params.setStr("encoding", EG64PosOccId);
    params.setStr("cookedEncoding", PosOccIdCooked);
}


template class EG2PosOccDecodeContext<true>;
template class EG2PosOccDecodeContext<false>;

template class EG2PosOccDecodeContextCooked<true>;
template class EG2PosOccDecodeContextCooked<false>;

template class EG2PosOccEncodeContext<true>;
template class EG2PosOccEncodeContext<false>;

template class EGPosOccDecodeContext<true>;
template class EGPosOccDecodeContext<false>;

template class EGPosOccDecodeContextCooked<true>;
template class EGPosOccDecodeContextCooked<false>;

template class EGPosOccEncodeContext<true>;
template class EGPosOccEncodeContext<false>;

} // namespace index

} // namespace search
