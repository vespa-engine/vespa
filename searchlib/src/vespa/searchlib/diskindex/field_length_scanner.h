// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <unordered_map>
#include <limits>
#include <cstdint>

namespace search::index { class DocIdAndFeatures; }

namespace search::diskindex {

/*
 * Class used to reconstruct field lengths based on element lengths in
 * posting list file.
 */
class FieldLengthScanner {
    class FieldLengthEntry {
        uint16_t _field_length;
        uint16_t _elements; // first 16 elements

        static uint16_t make_element_mask(uint32_t element_id) { return (1u << element_id); }

    public:
        FieldLengthEntry() noexcept
            : _field_length(0),
              _elements(0)
        {
        }

        void add_element_length(uint32_t element_length) {
            // Cap field length
            if (element_length < std::numeric_limits<uint16_t>::max()) {
                uint32_t field_length32 = _field_length + element_length;
                _field_length = std::min(field_length32, static_cast<uint32_t>(std::numeric_limits<uint16_t>::max()));
            } else {
                _field_length = std::numeric_limits<uint16_t>::max();
            }
        }

        void add_element_length(uint32_t element_length, uint32_t element_id) {
            uint16_t element_mask = make_element_mask(element_id);
            if (!(_elements & element_mask)) {
                _elements |= element_mask;
                add_element_length(element_length);
            }
        }

        uint16_t get_field_length() const { return _field_length; }
    };
    std::vector<FieldLengthEntry> _field_length_vector;
    static constexpr uint32_t element_id_bias = 16;
    // bit vectors for element >= element_id_bias
    std::unordered_map<uint32_t, std::vector<bool>> _scanned_elements_map;

public:
    FieldLengthScanner(uint32_t doc_id_limit);
    ~FieldLengthScanner();
    void scan_features(const index::DocIdAndFeatures &features);
    uint16_t get_field_length(uint32_t doc_id) const { return _field_length_vector[doc_id].get_field_length(); }
};

}
