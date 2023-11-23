// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_direct_posting_store.h"
#include <charconv>

namespace search {
namespace {
class StringAsKey final : public IDirectPostingStore::LookupKey {
public:
    StringAsKey(vespalib::stringref key)
        : _key(key)
    { }

    vespalib::stringref asString() const override { return _key; }
private:
    vespalib::string _key;
};
}

bool
IDirectPostingStore::LookupKey::asInteger(int64_t &value) const {
    vespalib::stringref str = asString();
    const char *end = str.data() + str.size();
    auto res = std::from_chars(str.data(), end, value);
    return res.ptr == end;
}

IDirectPostingStore::LookupResult
IDirectPostingStore::lookup(vespalib::stringref term, vespalib::datastore::EntryRef dictionary_snapshot) const {
    return lookup(StringAsKey(term), dictionary_snapshot);
}
}
