// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wordnummapper.h"
#include <vespa/fastos/file.h>

namespace search::diskindex {

const uint64_t WordNumMapping::_no_mapping[2] = { noWordNum(), noWordNumHigh() };

WordNumMapping::WordNumMapping()
    : _file(),
      _mapping()
{
}

WordNumMapping::WordNumMapping(WordNumMapping&&) noexcept = default;

WordNumMapping::~WordNumMapping() = default;

void
WordNumMapping::readMappingFile(const std::string &name)
{
    // Open word mapping file
    _file = std::make_unique<FastOS_File>();
    _file->enableMemoryMap(0);
    _file->OpenReadOnlyExisting(true,  name.c_str());
    int64_t tempfilesize = _file->getSize();
    size_t entries = static_cast<size_t>(tempfilesize / sizeof(uint64_t));
    const uint64_t* base = static_cast<const uint64_t*>(_file->MemoryMapPtr(0));
    _mapping = std::span<const uint64_t>(base, entries);
}


void
WordNumMapping::noMappingFile()
{
    _mapping = _no_mapping;
}

}
