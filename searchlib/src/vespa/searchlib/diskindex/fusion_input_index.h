// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docidmapper.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::diskindex {

/*
 * Class representing an index used as input for fusion.
 */
class FusionInputIndex
{
private:
    vespalib::string     _path;
    uint32_t             _index;
    const SelectorArray* _selector;
    index::Schema        _schema;
    DocIdMapping         _docIdMapping;

public:
    FusionInputIndex(const vespalib::string& path, uint32_t index, const SelectorArray& selector);
    FusionInputIndex(FusionInputIndex&&) = default;
    FusionInputIndex & operator = (FusionInputIndex&&) = default;
    ~FusionInputIndex();

    void setup();
    const vespalib::string& getPath() const noexcept { return _path; }
    uint32_t getIndex() const noexcept { return _index; }
    const DocIdMapping& getDocIdMapping() const noexcept { return _docIdMapping; }
    const index::Schema& getSchema() const noexcept { return _schema; }
};

}
