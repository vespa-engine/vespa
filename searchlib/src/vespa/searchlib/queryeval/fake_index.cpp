// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_index.h"

namespace search::queryeval {

FakeIndex::FakeIndex()
    : _current_doc(0),
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
FakeIndex::elem(uint32_t element_id, const std::string& layout)
{
    uint32_t len = layout.size();
    for (size_t pos = 0; pos < layout.size(); ++pos) {
        char ch = layout[pos];
        if (ch != '.') {
            auto& result = _terms[ch];
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
FakeIndex::lookup(char ch) const
{
    static FakeResult empty;
    auto it = _terms.find(ch);
    return (it != _terms.end()) ? it->second : empty;
}

}
