// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "posocccompression.h"
#include "posocc_fields_params.h"
#include "raw_features_collector.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/index/postinglistparams.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/data/fileheader.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".posocccompression");

using search::index::DocIdAndFeatures;
using search::index::PostingListParams;
using search::index::Schema;
using search::fef::TermFieldMatchData;
using vespalib::GenericHeader;
using namespace search::index;

namespace {

std::string PosOccId = "PosOcc.3";
std::string PosOccIdCooked = "PosOcc.3.Cooked";
std::string EG64PosOccId = "EG64PosOcc.3"; // Dynamic k values
std::string EG64PosOccId2 = "EG64PosOcc.2";    // Fixed k values

}

namespace search::bitcompression {


template <bool bigEndian>
void
EG2PosOccDecodeContext<bigEndian>::
readHeader(const vespalib::GenericHeader &header,
           const std::string &prefix)
{
    const_cast<PosOccFieldsParams *>(_fieldsParams)->readHeader(header, prefix);
}

template <bool bigEndian>
void
EG2PosOccDecodeContext<bigEndian>::
collect_raw_features_and_read_compr_buffer(RawFeaturesCollector& raw_features_collector, DocIdAndFeatures& features)
{
    raw_features_collector.collect_before_read_compr_buffer(*this, features);
    this->readComprBuffer();
    raw_features_collector.fixup_after_read_compr_buffer(*this);
}

template <bool bigEndian>
void
EG2PosOccDecodeContext<bigEndian>::
readFeatures(search::index::DocIdAndFeatures &features)
{
    RawFeaturesCollector raw_features_collector(*this, features);

    const PosOccFieldParams &fieldParams = _fieldsParams->getFieldParams()[0];
    uint32_t numElements = 1;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMELEMENTS, EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    const uint64_t *valE = _valE;
    for (uint32_t elementDone = 0; elementDone < numElements; ++elementDone) {
        if (fieldParams._hasElements) {
            UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTID, EC);
            if (fieldParams._hasElementWeights) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTWEIGHT, EC);
            }
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                collect_raw_features_and_read_compr_buffer(raw_features_collector, features);
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
        }
        UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTLEN, EC);
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMPOSITIONS, EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        do {
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                collect_raw_features_and_read_compr_buffer(raw_features_collector, features);
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
            UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_FIRST_WORDPOS, EC);
        } while (0);
        for (uint32_t pos = 1; pos < numPositions; ++pos) {
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                collect_raw_features_and_read_compr_buffer(raw_features_collector, features);
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
            UC64_SKIPEXPGOLOMB_SMALL_NS(o,K_VALUE_POSOCC_DELTA_WORDPOS, EC);
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
    raw_features_collector.finish(*this, features);
    this->readComprBufferIfNeeded();
}

template <bool bigEndian>
void
EG2PosOccDecodeContextCooked<bigEndian>::
readFeatures(search::index::DocIdAndFeatures &features)
{
    features.clear_features();
    features.set_has_raw_data(false);

    const PosOccFieldParams &fieldParams = _fieldsParams->getFieldParams()[0];
    uint32_t numElements = 1;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMELEMENTS, EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    const uint64_t *valE = _valE;
    uint32_t elementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements; ++elementDone, ++elementId) {
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTID, EC);
            elementId += static_cast<uint32_t>(val64);
        }
        features.elements().emplace_back(elementId);
        if (fieldParams._hasElementWeights) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTWEIGHT, EC);
            int32_t elementWeight = this->convertToSigned(val64);
            features.elements().back().setWeight(elementWeight);
        }
        if (__builtin_expect(oCompr >= valE, false)) {
            UC64_DECODECONTEXT_STORE(o, _);
            _readContext->readComprBuffer();
            valE = _valE;
            UC64_DECODECONTEXT_LOAD(o, _);
        }
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTLEN, EC);
        uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
        features.elements().back().setElementLen(elementLen);
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMPOSITIONS, EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        uint32_t wordPos = static_cast<uint32_t>(-1);
        do {
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_FIRST_WORDPOS, EC);
            wordPos = static_cast<uint32_t>(val64);
            features.elements().back().incNumOccs();
            features.word_positions().emplace_back(wordPos);
        } while (0);
        for (uint32_t pos = 1; pos < numPositions; ++pos) {
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_DELTA_WORDPOS, EC);
            wordPos += 1 + static_cast<uint32_t>(val64);
            features.elements().back().incNumOccs();
            features.word_positions().emplace_back(wordPos);
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
    this->readComprBufferIfNeeded();
}


template <bool bigEndian>
void
EG2PosOccDecodeContext<bigEndian>::
skipFeatures(unsigned int count)
{
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;

    const PosOccFieldParams &fieldParams = _fieldsParams->getFieldParams()[0];
    for (unsigned int i = count; i > 0; --i) {
        uint32_t numElements = 1;
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMELEMENTS, EC);
            numElements = static_cast<uint32_t>(val64) + 1;
        }
        for (uint32_t elementDone = 0; elementDone < numElements; ++elementDone) {
            if (fieldParams._hasElements) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTID, EC);
                if (fieldParams._hasElementWeights) {
                    UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTWEIGHT, EC);
                }
            }
            UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTLEN, EC);
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMPOSITIONS, EC);
            uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

            UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_FIRST_WORDPOS, EC);
            for (uint32_t pos = 1; pos < numPositions; ++pos) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_DELTA_WORDPOS, EC);
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

    const PosOccFieldParams &fieldParams = _fieldsParams->getFieldParams()[0];
    uint32_t numElements = 1;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMELEMENTS, EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    TermFieldMatchData *tfmd = matchData[0];
    tfmd->reset(docId);
    tfmd->clear_hidden_from_ranking();
    uint32_t elementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements; ++elementDone, ++elementId) {
        int32_t elementWeight = 1;
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTID, EC);
            elementId += static_cast<uint32_t>(val64);
            if (fieldParams._hasElementWeights) {
                UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTWEIGHT, EC);
                elementWeight = this->convertToSigned(val64);
            }
        }

        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTLEN, EC);
        uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMPOSITIONS, EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_FIRST_WORDPOS, EC);
        uint32_t wordPos = static_cast<uint32_t>(val64);
        {
            search::fef::TermFieldMatchDataPosition pos(elementId, wordPos, elementWeight, elementLen);
            tfmd->appendPosition(pos);
        }
        for (uint32_t wi = 1; wi < numPositions; ++wi) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_DELTA_WORDPOS, EC);
            wordPos += 1 + static_cast<uint32_t>(val64);
            {
                search::fef::TermFieldMatchDataPosition pos(elementId, wordPos, elementWeight, elementLen);
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
           const std::string &prefix)
{
    const_cast<PosOccFieldsParams *>(_fieldsParams)->readHeader(header,
            prefix);
}


template <bool bigEndian>
const std::string &
EG2PosOccDecodeContext<bigEndian>::getIdentifier() const
{
    return EG64PosOccId2;
}


template <bool bigEndian>
void
EG2PosOccEncodeContext<bigEndian>::
writeHeader(vespalib::GenericHeader &header,
            const std::string &prefix) const
{
    _fieldsParams->writeHeader(header, prefix);
}


template <bool bigEndian>
const std::string &
EG2PosOccEncodeContext<bigEndian>::getIdentifier() const
{
    return EG64PosOccId2;
}


template <bool bigEndian>
void
EG2PosOccEncodeContext<bigEndian>::
writeFeatures(const search::index::DocIdAndFeatures &features)
{
    if (features.has_raw_data()) {
        writeBits(features.blob().data(), features.bit_offset(), features.bit_length());
        return;
    }

    auto element = features.elements().begin();
    auto position = features.word_positions().begin();

    const PosOccFieldParams &fieldParams = _fieldsParams->getFieldParams()[0];

    uint32_t numElements = features.elements().size();
    if (fieldParams._hasElements) {
        assert(numElements > 0u);
        encodeExpGolomb(numElements - 1, K_VALUE_POSOCC_NUMELEMENTS);
    } else {
        assert(numElements == 1);
    }
    uint32_t minElementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements;
         ++elementDone, ++element) {
        if (fieldParams._hasElements) {
            uint32_t elementId = element->getElementId();
            assert(elementId >= minElementId);
            encodeExpGolomb(elementId - minElementId, K_VALUE_POSOCC_ELEMENTID);
            minElementId = elementId + 1;
            if (fieldParams._hasElementWeights) {
                int32_t elementWeight = element->getWeight();
                encodeExpGolomb(this->convertToUnsigned(elementWeight), K_VALUE_POSOCC_ELEMENTWEIGHT);
            }
            if (__builtin_expect(_valI >= _valE, false)) {
                _writeContext->writeComprBuffer(false);
            }
        } else {
            uint32_t elementId = element->getElementId();
            assert(elementId == 0);
            (void) elementId;
        }

        encodeExpGolomb(element->getElementLen() - 1, K_VALUE_POSOCC_ELEMENTLEN);
        uint32_t numPositions = element->getNumOccs();
        assert(numPositions > 0);
        encodeExpGolomb(numPositions - 1, K_VALUE_POSOCC_NUMPOSITIONS);

        uint32_t wordPos = static_cast<uint32_t>(-1);
        do {
            uint32_t lastWordPos = wordPos;
            wordPos = position->getWordPos();
            encodeExpGolomb(wordPos - lastWordPos - 1, K_VALUE_POSOCC_FIRST_WORDPOS);
            if (__builtin_expect(_valI >= _valE, false)) {
                _writeContext->writeComprBuffer(false);
            }
            ++position;
        } while (0);
        uint32_t positionResidue = numPositions - 1;
        while (positionResidue > 0) {
            uint32_t lastWordPos = wordPos;
            wordPos = position->getWordPos();
            encodeExpGolomb(wordPos - lastWordPos - 1, K_VALUE_POSOCC_DELTA_WORDPOS);
            if (__builtin_expect(_valI >= _valE, false)) {
                _writeContext->writeComprBuffer(false);
            }
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
           const std::string &prefix)
{
    ParentClass::readHeader(header, prefix);
}


template <bool bigEndian>
void
EGPosOccDecodeContext<bigEndian>::
readFeatures(search::index::DocIdAndFeatures &features)
{
    RawFeaturesCollector raw_features_collector(*this, features);

    const PosOccFieldParams &fieldParams = _fieldsParams->getFieldParams()[0];
    uint32_t elementLenK = EGPosOccEncodeContext<bigEndian>::
                           calcElementLenK(fieldParams._avgElemLen);
    uint32_t numElements = 1;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMELEMENTS, EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    const uint64_t *valE = _valE;
    for (uint32_t elementDone = 0; elementDone < numElements; ++elementDone) {
        if (fieldParams._hasElements) {
            UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTID, EC);
            if (fieldParams._hasElementWeights) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTWEIGHT, EC);
            }
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                collect_raw_features_and_read_compr_buffer(raw_features_collector, features);
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
        }
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, elementLenK, EC);
        uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMPOSITIONS, EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        uint32_t wordPosK = EGPosOccEncodeContext<bigEndian>::calcWordPosK(numPositions, elementLen);

        for (uint32_t pos = 0; pos < numPositions; ++pos) {
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                collect_raw_features_and_read_compr_buffer(raw_features_collector, features);
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
            UC64_SKIPEXPGOLOMB_SMALL_NS(o, wordPosK, EC);
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
    raw_features_collector.finish(*this, features);
    this->readComprBufferIfNeeded();
}


template <bool bigEndian>
void
EGPosOccDecodeContextCooked<bigEndian>::
readFeatures(search::index::DocIdAndFeatures &features)
{
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _);
    uint32_t length;
    uint64_t val64;

    features.clear_features();
    features.set_has_raw_data(false);

    const PosOccFieldParams &fieldParams = _fieldsParams->getFieldParams()[0];
    uint32_t elementLenK = EGPosOccEncodeContext<bigEndian>::
                           calcElementLenK(fieldParams._avgElemLen);
    uint32_t numElements = 1;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o,
                                      K_VALUE_POSOCC_NUMELEMENTS,
                                      EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    const uint64_t *valE = _valE;
    uint32_t elementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements; ++elementDone, ++elementId) {
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTID, EC);
            elementId += static_cast<uint32_t>(val64);
        }
        features.elements().emplace_back(elementId);
        if (fieldParams._hasElementWeights) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTWEIGHT, EC);
            int32_t elementWeight = this->convertToSigned(val64);
            features.elements().back().setWeight(elementWeight);
        }
        if (__builtin_expect(oCompr >= valE, false)) {
            UC64_DECODECONTEXT_STORE(o, _);
            _readContext->readComprBuffer();
            valE = _valE;
            UC64_DECODECONTEXT_LOAD(o, _);
        }
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, elementLenK, EC);
        uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
        features.elements().back().setElementLen(elementLen);
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMPOSITIONS, EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        uint32_t wordPosK = EGPosOccEncodeContext<bigEndian>::calcWordPosK(numPositions, elementLen);

        uint32_t wordPos = static_cast<uint32_t>(-1);
        for (uint32_t pos = 0; pos < numPositions; ++pos) {
            if (__builtin_expect(oCompr >= valE, false)) {
                UC64_DECODECONTEXT_STORE(o, _);
                _readContext->readComprBuffer();
                valE = _valE;
                UC64_DECODECONTEXT_LOAD(o, _);
            }
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, wordPosK, EC);
            wordPos += 1 + static_cast<uint32_t>(val64);
            features.elements().back().incNumOccs();
            features.word_positions().emplace_back(wordPos);
        }
    }
    UC64_DECODECONTEXT_STORE(o, _);
    this->readComprBufferIfNeeded();
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
        const PosOccFieldParams &fieldParams = _fieldsParams->getFieldParams()[0];
        uint32_t elementLenK = EGPosOccEncodeContext<bigEndian>::calcElementLenK(fieldParams._avgElemLen);
        uint32_t numElements = 1;
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMELEMENTS, EC);
            numElements = static_cast<uint32_t>(val64) + 1;
        }
        for (uint32_t elementDone = 0; elementDone < numElements; ++elementDone) {
            if (fieldParams._hasElements) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTID, EC);
                if (fieldParams._hasElementWeights) {
                    UC64_SKIPEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTWEIGHT, EC);
                }
            }
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, elementLenK, EC);
            uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMPOSITIONS, EC);
            uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

            uint32_t wordPosK = EGPosOccEncodeContext<bigEndian>::calcWordPosK(numPositions, elementLen);

            for (uint32_t pos = 0; pos < numPositions; ++pos) {
                UC64_SKIPEXPGOLOMB_SMALL_NS(o, wordPosK, EC);
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
    uint32_t elementLenK = EGPosOccEncodeContext<bigEndian>::calcElementLenK(fieldParams._avgElemLen);
    uint32_t numElements = 1;
    if (fieldParams._hasElements) {
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMELEMENTS, EC);
        numElements = static_cast<uint32_t>(val64) + 1;
    }
    TermFieldMatchData *tfmd = matchData[0];
    tfmd->reset(docId);
    tfmd->clear_hidden_from_ranking();
    uint32_t elementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements; ++elementDone, ++elementId) {
        int32_t elementWeight = 1;
        if (fieldParams._hasElements) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTID, EC);
            elementId += static_cast<uint32_t>(val64);
            if (fieldParams._hasElementWeights) {
                UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_ELEMENTWEIGHT, EC);
                elementWeight = this->convertToSigned(val64);
            }
        }
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, elementLenK, EC);
        uint32_t elementLen = static_cast<uint32_t>(val64) + 1;
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_POSOCC_NUMPOSITIONS, EC);
        uint32_t numPositions = static_cast<uint32_t>(val64) + 1;

        uint32_t wordPosK = EGPosOccEncodeContext<bigEndian>::calcWordPosK(numPositions, elementLen);

        UC64_DECODEEXPGOLOMB_SMALL_NS(o, wordPosK, EC);
        uint32_t wordPos = static_cast<uint32_t>(val64);
        {
            search::fef::TermFieldMatchDataPosition pos(elementId, wordPos, elementWeight, elementLen);
            tfmd->appendPosition(pos);
        }
        for (uint32_t wi = 1; wi < numPositions; ++wi) {
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, wordPosK, EC);
            wordPos += 1 + static_cast<uint32_t>(val64);
            {
                search::fef::TermFieldMatchDataPosition pos(elementId, wordPos, elementWeight, elementLen);
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
           const std::string &prefix)
{
    ParentClass::readHeader(header, prefix);
}


template <bool bigEndian>
const std::string &
EGPosOccDecodeContext<bigEndian>::getIdentifier() const
{
    return EG64PosOccId;
}


template <bool bigEndian>
void
EGPosOccEncodeContext<bigEndian>::
writeHeader(vespalib::GenericHeader &header,
            const std::string &prefix) const
{
    ParentClass::writeHeader(header, prefix);
}


template <bool bigEndian>
const std::string &
EGPosOccEncodeContext<bigEndian>::getIdentifier() const
{
    return EG64PosOccId;
}


template <bool bigEndian>
void
EGPosOccEncodeContext<bigEndian>::
writeFeatures(const search::index::DocIdAndFeatures &features)
{
    if (features.has_raw_data()) {
        writeBits(features.blob().data(),
                  features.bit_offset(), features.bit_length());
        return;
    }

    auto element = features.elements().begin();
    auto position = features.word_positions().begin();
    const PosOccFieldParams &fieldParams =
        _fieldsParams->getFieldParams()[0];
    uint32_t elementLenK = calcElementLenK(fieldParams._avgElemLen);

    uint32_t numElements = features.elements().size();
    if (fieldParams._hasElements) {
        assert(numElements > 0u);
        encodeExpGolomb(numElements - 1,
                        K_VALUE_POSOCC_NUMELEMENTS);
    } else {
        assert(numElements == 1);
    }
    uint32_t minElementId = 0;
    for (uint32_t elementDone = 0; elementDone < numElements; ++elementDone, ++element) {
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
            if (__builtin_expect(_valI >= _valE, false)) {
                _writeContext->writeComprBuffer(false);
            }
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
            if (__builtin_expect(_valI >= _valE, false)) {
                _writeContext->writeComprBuffer(false);
            }
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

}
