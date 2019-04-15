// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/fslimits.h>
#include <vector>
#include <cstdint>

namespace search::index {

/*
 * The following feature classes are not self contained.  To reduce
 * memory allocator pressure, the DocIdAndFeatures class contains a
 * flattened representation of the features at different levels.
 */

/*
 * (word, doc) features.
 *
 * Present as member in DocIdAndFeatures.
 */
class WordDocFeatures {
public:
    // TODO: add support for user features

    WordDocFeatures() { }
    void clear() { }
};

/*
 * (word, doc, field) features.
 *
 * Present as vector element in DocIdAndFeatures.
 */
class WordDocFieldFeatures {
public:
    uint32_t _numElements;  // Number of array indexes
    // TODO: add support for user features

    WordDocFieldFeatures()
        : _numElements(0u)
    {}

    uint32_t getNumElements() const { return _numElements; }
    void setNumElements(uint32_t numElements) { _numElements = numElements; }
    void incNumElements() { ++_numElements; }
};

/*
 * (word, doc, field, element) features.
 *
 * Present as vector element in DocIdAndFeatures.
 */
class WordDocElementFeatures {
public:
    uint32_t _elementId;    // Array index
    uint32_t _numOccs;
    int32_t _weight;
    uint32_t _elementLen;
    // TODO: add support for user features

    WordDocElementFeatures()
        : _elementId(0u),
          _numOccs(0u),
          _weight(1),
          _elementLen(SEARCHLIB_FEF_UNKNOWN_FIELD_LENGTH)
    {}

    WordDocElementFeatures(uint32_t elementId)
        : _elementId(elementId),
          _numOccs(0u),
          _weight(1),
          _elementLen(SEARCHLIB_FEF_UNKNOWN_FIELD_LENGTH)
    {}

    WordDocElementFeatures(uint32_t elementId,
                           uint32_t weight,
                           uint32_t elementLen)
        : _elementId(elementId),
          _numOccs(0u),
          _weight(weight),
          _elementLen(elementLen)
    {}

    uint32_t getElementId() const { return _elementId; } 
    uint32_t getNumOccs() const { return _numOccs; } 
    int32_t getWeight() const { return _weight; } 
    uint32_t getElementLen() const { return _elementLen; }

    void setElementId(uint32_t elementId) { _elementId = elementId; }
    void setNumOccs(uint32_t numOccs) { _numOccs = numOccs; }
    void setWeight(int32_t weight) { _weight = weight; }
    void setElementLen(uint32_t elementLen) { _elementLen = elementLen; }
    void incNumOccs() { ++_numOccs; }
};

/*
 * (word, doc, field, element, wordpos) features.
 *
 * Present as vector element in DocIdAndFeatures.
 */
class WordDocElementWordPosFeatures {
public:
    uint32_t _wordPos;
    // TODO: add support for user features

    WordDocElementWordPosFeatures()
        : _wordPos(0u)
    {}

    WordDocElementWordPosFeatures(uint32_t wordPos)
        : _wordPos(wordPos)
    {}

    uint32_t getWordPos() const { return _wordPos; }
    void setWordPos(uint32_t wordPos) { _wordPos = wordPos; }
};

/**
 * Class for minimal common representation of features available for a
 * (word, doc) pair, used by index fusion to shuffle information from
 * input files to the output file without having to know all the details.
 */
class DocIdAndFeatures {
public:
    uint32_t _docId;            // Current Docid
    // generic feature data, flattened to avoid excessive allocator usage
    WordDocFeatures _wordDocFeatures;
    std::vector<WordDocElementFeatures> _elements;
    std::vector<WordDocElementWordPosFeatures> _wordPositions;
#ifdef notyet
    // user blobs (packed)
    UserFeatures _userFeatures;
    // TODO: Determine how to handle big endian versus little endian user
    // features, and whether set of user features is contiguous in file or
    // interleaved with predefined features (word position, word weight)
#endif
    // raw data (file format specific, packed)
    std::vector<uint64_t> _blob; // Feature data for (word, docid) pair
    uint32_t _bitOffset;         // Offset of feature start ([0..63])
    uint32_t _bitLength;         // Length of features
    bool _raw;                   //

    DocIdAndFeatures();
    DocIdAndFeatures(const DocIdAndFeatures &);
    DocIdAndFeatures & operator = (const DocIdAndFeatures &);
    DocIdAndFeatures(DocIdAndFeatures &&) = default;
    DocIdAndFeatures & operator = (DocIdAndFeatures &&) = default;
    ~DocIdAndFeatures();

    void clearFeatures() {
        _wordDocFeatures.clear();
        _elements.clear();
        _wordPositions.clear();
        _bitOffset = 0u;
        _bitLength = 0u;
        _blob.clear();
    }

    void clearFeatures(uint32_t bitOffset) {
        _wordDocFeatures.clear();
        _elements.clear();
        _wordPositions.clear();
        _bitOffset = bitOffset;
        _bitLength = 0u;
        _blob.clear();
    }

    void clear(uint32_t docId) {
        _docId = docId;
        clearFeatures();
    }


    void clear(uint32_t docId, uint32_t bitOffset) {
        _docId = docId;
        clearFeatures(bitOffset);
    }

    void setRaw(bool raw) { _raw = raw; }
    bool getRaw() const { return _raw; }
};

}
