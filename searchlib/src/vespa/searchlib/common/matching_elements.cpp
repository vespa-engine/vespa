// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matching_elements.h"
#include <algorithm>

namespace search {

MatchingElements::MatchingElements() = default;
MatchingElements::~MatchingElements() = default;

void
MatchingElements::add_matching_elements(uint32_t docid, const vespalib::string &field_name, const std::vector<uint32_t> &elements)
{
    auto &list = _map[key_t(docid, field_name)];
    std::vector<uint32_t> new_list;
    std::set_union(list.begin(), list.end(), elements.begin(), elements.end(), std::back_inserter(new_list));
    list = std::move(new_list);
}

const std::vector<uint32_t> &
MatchingElements::get_matching_elements(uint32_t docid, const vespalib::string &field_name) const
{
    static const std::vector<uint32_t> empty;
    auto res = _map.find(key_t(docid, field_name));
    if (res == _map.end()) {
        return empty;
    }
    return res->second;
}

} // namespace search
