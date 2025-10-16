// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_index.h"
#include <vespa/searchlib/query/streaming/hit.h>

namespace search::queryeval {

FakeIndex::FakeIndex()
    : _current_doc(0),
      _current_field(0),
      _terms()
{
}

FakeIndex::~FakeIndex() = default;

FakeIndex&
FakeIndex::doc(uint32_t docid)
{
    _current_doc = docid;
    return *this;
}

FakeIndex&
FakeIndex::field(uint32_t field_id)
{
    _current_field = field_id;
    return *this;
}

FakeIndex&
FakeIndex::elem(uint32_t element_id, const std::string& layout)
{
    uint32_t len = layout.size();
    for (size_t pos = 0; pos < layout.size(); ++pos) {
        char ch = layout[pos];
        if (ch != '.') {
            auto key = std::make_pair(ch, _current_field);
            auto& result = _terms[key];
            if (result.inspect().empty() || result.inspect().back().docId != _current_doc) {
                result.doc(_current_doc);
            }
            if (result.inspect().back().elements.empty() ||
                result.inspect().back().elements.back().id != element_id) {
                result.elem(element_id).len(len);
            }
            result.pos((uint32_t)pos);
        }
    }
    return *this;
}

const FakeResult&
FakeIndex::lookup(char ch, uint32_t field_id) const
{
    static FakeResult empty;
    auto it = _terms.find(std::make_pair(ch, field_id));
    return (it != _terms.end()) ? it->second : empty;
}

std::vector<search::streaming::Hit>
FakeIndex::get_streaming_hits(char ch, uint32_t docid, std::optional<std::vector<uint32_t>> field_ids) const
{
    std::vector<search::streaming::Hit> result;

    if (field_ids.has_value()) {
        // Use specified fields
        for (uint32_t field_id : field_ids.value()) {
            auto it = _terms.find(std::make_pair(ch, field_id));
            if (it != _terms.end()) {
                auto hits = it->second.get_streaming_hits(docid, field_id);
                result.insert(result.end(), hits.begin(), hits.end());
            }
        }
    } else {
        // Use all fields that have this term
        for (const auto& [key, fake_result] : _terms) {
            if (key.first == ch) {
                uint32_t field_id = key.second;
                auto hits = fake_result.get_streaming_hits(docid, field_id);
                result.insert(result.end(), hits.begin(), hits.end());
            }
        }
    }

    return result;
}

}
