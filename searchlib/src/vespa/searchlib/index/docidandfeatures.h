// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/fslimits.h>
#include <vector>
#include <cstdint>

namespace search::index {

/**
 * The following feature classes are not self contained.  To reduce
 * memory allocator pressure, the DocIdAndFeatures class contains a
 * flattened representation of the features at different levels.
 */

/**
 * (word, doc, element) features.
 *
 * Present as vector element in DocIdAndFeatures.
 */
class WordDocElementFeatures {
private:
    uint32_t _elementId;    // Array index
    uint32_t _numOccs;
    int32_t _weight;
    uint32_t _elementLen;

public:
    WordDocElementFeatures() noexcept
        : _elementId(0u),
          _numOccs(0u),
          _weight(1),
          _elementLen(SEARCHLIB_FEF_UNKNOWN_FIELD_LENGTH)
    {}

    WordDocElementFeatures(uint32_t elementId) noexcept
        : _elementId(elementId),
          _numOccs(0u),
          _weight(1),
          _elementLen(SEARCHLIB_FEF_UNKNOWN_FIELD_LENGTH)
    {}

    WordDocElementFeatures(uint32_t elementId,
                           uint32_t weight,
                           uint32_t elementLen) noexcept
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

/**
 * (word, doc, element, wordpos) features.
 *
 * Present as vector element in DocIdAndFeatures.
 */
class WordDocElementWordPosFeatures {
private:
    uint32_t _wordPos;

public:
    WordDocElementWordPosFeatures() noexcept
        : _wordPos(0u)
    {}

    WordDocElementWordPosFeatures(uint32_t wordPos) noexcept
        : _wordPos(wordPos)
    {}

    uint32_t getWordPos() const { return _wordPos; }
    void setWordPos(uint32_t wordPos) { _wordPos = wordPos; }
};

/**
 * Class for minimal common representation of features available for a (word, doc) pair.
 *
 * Used in memory index and disk index posting lists and by index fusion to shuffle information from
 * input files to the output file without having to know all the details.
 */
class DocIdAndFeatures {
public:
    using RawData = std::vector<uint64_t>;

protected:
    uint32_t _doc_id; // Current document id
    uint32_t _field_length;
    uint32_t _num_occs;
    std::vector<WordDocElementFeatures> _elements;
    std::vector<WordDocElementWordPosFeatures> _word_positions;

    // Raw data (file format specific, packed)
    RawData  _blob; // Feature data for (word, docid) pair
    uint32_t _bit_offset; // Offset of feature start ([0..63])
    uint32_t _bit_length; // Length of features
    bool     _has_raw_data;

public:
    DocIdAndFeatures();
    DocIdAndFeatures(const DocIdAndFeatures &);
    DocIdAndFeatures & operator = (const DocIdAndFeatures &);
    DocIdAndFeatures(DocIdAndFeatures &&) noexcept = default;
    DocIdAndFeatures & operator = (DocIdAndFeatures &&) noexcept = default;
    ~DocIdAndFeatures();

    void clear_features() {
        _elements.clear();
        _word_positions.clear();
        _bit_offset = 0u;
        _bit_length = 0u;
        _blob.clear();
    }

    void clear_features(uint32_t bit_offset) {
        _elements.clear();
        _word_positions.clear();
        _bit_offset = bit_offset;
        _bit_length = 0u;
        _blob.clear();
    }

    void clear(uint32_t doc_id) {
        _doc_id = doc_id;
        clear_features();
    }


    void clear(uint32_t doc_id, uint32_t bit_offset) {
        _doc_id = doc_id;
        clear_features(bit_offset);
    }

    uint32_t doc_id() const { return _doc_id; }
    uint32_t field_length() const { return _field_length; }
    uint32_t num_occs() const { return _num_occs; }
    void set_doc_id(uint32_t val) { _doc_id = val; }
    void set_field_length(uint32_t val) { _field_length = val; }
    void set_num_occs(uint32_t val) { _num_occs = val; }

    const std::vector<WordDocElementFeatures>& elements() const { return _elements; }
    std::vector<WordDocElementFeatures>& elements() { return _elements; }

    const std::vector<WordDocElementWordPosFeatures>& word_positions() const { return _word_positions; }
    std::vector<WordDocElementWordPosFeatures>& word_positions() { return _word_positions; }

    const RawData& blob() const { return _blob; }
    RawData& blob() { return _blob; }
    uint32_t bit_offset() const { return _bit_offset; }
    uint32_t bit_length() const { return _bit_length; }
    void set_bit_length(uint32_t val) { _bit_length = val; }
    bool has_raw_data() const { return _has_raw_data; }
    void set_has_raw_data(bool val) { _has_raw_data = val; }
};

class DocIdAndPosOccFeatures : public DocIdAndFeatures
{
public:
    void addNextOcc(uint32_t elementId, uint32_t wordPos, int32_t elementWeight, uint32_t elementLen);
};

}
