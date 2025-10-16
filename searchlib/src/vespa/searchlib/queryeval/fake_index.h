// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fake_result.h"
#include <map>
#include <optional>
#include <string>
#include <vector>

namespace search::queryeval {

/**
 * Visual test data builder for creating FakeResult objects from string layouts.
 *
 * Allows defining test data visually where each character position represents
 * a term occurrence. Use '.' for empty positions and letters (A-Z, a-z) for terms.
 *
 * Supports multiple fields for both indexed and streaming search tests.
 *
 * Example usage (single field):
 *   FakeIndex index;
 *   index.doc(69).elem(0, "..A..B..")
 *                .elem(1, ".C...D..");
 *   auto a = index.lookup('A');  // FakeResult with A's positions in field 0
 *
 * Example usage (multi-field):
 *   FakeIndex index;
 *   index.doc(69).field(0).elem(0, "..A..B..")
 *                .field(1).elem(0, "..A..C..");
 *   auto a0 = index.lookup('A', 0);  // FakeResult for field 0
 *   auto a1 = index.lookup('A', 1);  // FakeResult for field 1
 */
class FakeIndex {
private:
    uint32_t _current_doc;
    uint32_t _current_field;
    std::map<std::pair<char, uint32_t>, FakeResult> _terms;

public:
    FakeIndex();
    ~FakeIndex();

    /**
     * Start adding data for a new document.
     * @param docid The document ID
     */
    FakeIndex& doc(uint32_t docid);

    /**
     * Set the current field for subsequent elem() calls.
     * @param field_id The field ID (default: 0)
     */
    FakeIndex& field(uint32_t field_id);

    /**
     * Add an element with visual layout.
     * @param element_id The element ID
     * @param layout String where each character is either '.' (empty) or a term letter
     */
    FakeIndex& elem(uint32_t element_id, const std::string& layout);

    /**
     * Lookup the FakeResult for a given term character in a specific field.
     * @param ch The term character
     * @param field_id The field ID (default: 0)
     * @return FakeResult containing all occurrences of this term in the field
     */
    const FakeResult& lookup(char ch, uint32_t field_id = 0) const;

    /**
     * Extract hits for streaming search, aggregating across specified fields.
     * For streaming search, hits from all specified fields are combined into a single list.
     * @param ch The term character
     * @param docid The document ID to extract
     * @param field_ids Optional vector of field IDs to include (nullopt = all fields with this term)
     * @return Vector of streaming Hit objects for this term across specified/all fields
     */
    std::vector<search::streaming::Hit> get_streaming_hits(char ch, uint32_t docid,
                                                            std::optional<std::vector<uint32_t>> field_ids = std::nullopt) const;
};

}
