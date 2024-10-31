// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dictionary_lookup_result.h"

namespace search::index {

DictionaryLookupResult::DictionaryLookupResult() noexcept
    : wordNum(0),
      counts(),
      bitOffset(0)
{
}

DictionaryLookupResult::~DictionaryLookupResult() = default;

void swap(DictionaryLookupResult& a, DictionaryLookupResult& b) noexcept
{
    a.swap(b);
}

}
