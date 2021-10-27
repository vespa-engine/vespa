// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_length_scanner.h"
#include <vespa/searchlib/index/docidandfeatures.h>

namespace search::diskindex {

FieldLengthScanner::FieldLengthScanner(uint32_t doc_id_limit)
    : _field_length_vector(doc_id_limit),
      _scanned_elements_map()
{
}

FieldLengthScanner::~FieldLengthScanner() = default;

void
FieldLengthScanner::scan_features(const index::DocIdAndFeatures &features)
{
    if (features.elements().empty()) {
        return;
    }
    auto &entry = _field_length_vector[features.doc_id()];
    if (features.elements().back().getElementId() < element_id_bias) {
        for (const auto &element : features.elements()) {
            entry.add_element_length(element.getElementLen(), element.getElementId());
        }
    } else {
        auto element = features.elements().cbegin();
        while (element->getElementId() < element_id_bias) {
            entry.add_element_length(element->getElementLen(), element->getElementId());
            ++element;
        }
        auto &scanned_elements = _scanned_elements_map[features.doc_id()];
        auto size_needed = features.elements().back().getElementId() + 1 - element_id_bias;
        if (size_needed > scanned_elements.size()) {
            if (size_needed > scanned_elements.capacity()) {
                scanned_elements.reserve(std::max(size_needed + (size_needed / 4), 32u));
            }
            scanned_elements.resize(size_needed);
        }
        while (element != features.elements().cend()) {
            if (!scanned_elements[element->getElementId() - element_id_bias]) {
                scanned_elements[element->getElementId() - element_id_bias] = true;
                entry.add_element_length(element->getElementLen());
            }
            ++element;
        }
    }
}

}
