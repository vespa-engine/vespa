// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ordered_field_index_inserter_backend.h"
#include <vespa/searchlib/index/docidandfeatures.h>

namespace search::memoryindex::test {

OrderedFieldIndexInserterBackend::OrderedFieldIndexInserterBackend()
    : _ss(),
      _first(true),
      _verbose(false),
      _show_interleaved_features(false)
{
}

OrderedFieldIndexInserterBackend::~OrderedFieldIndexInserterBackend() = default;

void
OrderedFieldIndexInserterBackend::addComma()
{
    if (!_first) {
        _ss << ",";
    } else {
        _first = false;
    }
}

void
OrderedFieldIndexInserterBackend::setNextWord(const vespalib::stringref word)
{
    addComma();
    _ss << "w=" << word;
}

void
OrderedFieldIndexInserterBackend::add(uint32_t docId, const index::DocIdAndFeatures &features)
{
    (void) features;
    addComma();
    _ss << "a=" << docId;
    if (_verbose) {
        _ss << "(";
        auto wpi = features.word_positions().begin();
        bool firstElement = true;
        if (_show_interleaved_features) {
            _ss << "fl=" << features.field_length() <<
                ",occs=" << features.num_occs();
            firstElement = false;
        }
        for (auto &el : features.elements()) {
            if (!firstElement) {
                _ss << ",";
            }
            firstElement = false;
            _ss << "e=" << el.getElementId() << ",w=" <<
                el.getWeight() <<  ",l=" <<
                el.getElementLen() << "[";
            bool firstWordPos = true;
            for (uint32_t i = 0; i < el.getNumOccs(); ++i) {
                if (!firstWordPos) {
                    _ss << ",";
                }
                firstWordPos = false;
                _ss << wpi->getWordPos();
                ++wpi;
            }
            _ss << "]";
        }
        _ss << ")";
    }
}

void
OrderedFieldIndexInserterBackend::remove(uint32_t docId)
{
    addComma();
    _ss << "r=" << docId;
}

void
OrderedFieldIndexInserterBackend::rewind(uint32_t field_id)
{
    addComma();
    _ss << "f=" << field_id;
}

std::string
OrderedFieldIndexInserterBackend::toStr() const
{
    return _ss.str();
}

void
OrderedFieldIndexInserterBackend::reset()
{
    _ss.str("");
    _first = true;
    _verbose = false;
}

}
