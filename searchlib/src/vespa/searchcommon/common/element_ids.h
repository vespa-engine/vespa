// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <span>
#include <vector>

namespace search::common {

/*
 * The selected element ids for a multi-value summary field, cf. SummaryElementsSelector or for unpacked
 * match data.
 *
 * If _element_ids.data() is nullptr then all elements are selected.
 */
class ElementIds {
    const std::span<const uint32_t> _element_ids;
    static const std::span<const uint32_t> _empty; // empty span where data() is not nullptr
    ElementIds() noexcept : _element_ids() { } // default std::span constructor gives nullptr for data()
public:
    explicit ElementIds(const std::vector<uint32_t>& element_ids) noexcept
        : _element_ids(element_ids.empty() ? _empty : element_ids)
    {
        // _elements_ids.data() is known to not be nullptr here since _empty was used if vector was empty.
    }
    const uint32_t& back() const noexcept { return _element_ids.back(); }
    std::span<const uint32_t>::iterator begin() const noexcept { return _element_ids.begin(); }
    std::span<const uint32_t>::iterator end() const noexcept { return _element_ids.end(); }
    bool empty() const noexcept { return _element_ids.empty(); }
    bool all_elements() const noexcept { return _element_ids.data() == nullptr; }
    static ElementIds select_all() noexcept { return ElementIds(); }
};

}
