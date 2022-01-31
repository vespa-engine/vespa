// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docidandfeatures.h"
#include <cassert>

namespace search::index {

DocIdAndFeatures::DocIdAndFeatures()
    : _doc_id(0),
      _field_length(1),
      _num_occs(1),
      _elements(),
      _word_positions(),
      _blob(),
      _bit_offset(0u),
      _bit_length(0u),
      _has_raw_data(false)
{
}

DocIdAndFeatures::DocIdAndFeatures(const DocIdAndFeatures &) = default;
DocIdAndFeatures & DocIdAndFeatures::operator = (const DocIdAndFeatures &) = default;
DocIdAndFeatures::~DocIdAndFeatures() = default;

void
DocIdAndPosOccFeatures::addNextOcc(uint32_t elementId, uint32_t wordPos, int32_t elementWeight, uint32_t elementLen)
{
    assert(wordPos < elementLen);
    if (_elements.empty() || elementId > _elements.back().getElementId()) {
        _elements.emplace_back(elementId, elementWeight, elementLen);
    } else {
        assert(elementId == _elements.back().getElementId());
        assert(elementWeight == _elements.back().getWeight());
        assert(elementLen == _elements.back().getElementLen());
    }
    assert(_elements.back().getNumOccs() == 0 ||
           wordPos > _word_positions.back().getWordPos());
    _elements.back().incNumOccs();
    _word_positions.emplace_back(wordPos);
}

}
