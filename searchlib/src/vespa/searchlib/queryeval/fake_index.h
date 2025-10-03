// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fake_result.h"
#include <map>
#include <string>

namespace search::queryeval {

/**
 * Visual test data builder for creating FakeResult objects from string layouts.
 *
 * Allows defining test data visually where each character position represents
 * a term occurrence. Use '.' for empty positions and letters (A-Z, a-z) for terms.
 *
 * Example usage:
 *   FakeIndex index;
 *   index.doc(69).elem(0, "..A..B..")
 *                .elem(1, ".C...D..");
 *   auto a = index.lookup('A');  // FakeResult with A's positions
 *   auto b = index.lookup('B');
 */
class FakeIndex {
private:
    uint32_t _current_doc;
    std::map<char, FakeResult> _terms;

public:
    FakeIndex();

    /**
     * Start adding data for a new document.
     * @param docid The document ID
     */
    FakeIndex& doc(uint32_t docid);

    /**
     * Add an element with visual layout.
     * @param element_id The element ID
     * @param layout String where each character is either '.' (empty) or a term letter
     */
    FakeIndex& elem(uint32_t element_id, const std::string& layout);

    /**
     * Lookup the FakeResult for a given term character.
     * @param ch The term character
     * @return FakeResult containing all occurrences of this term
     */
    const FakeResult& lookup(char ch) const;
};

}
