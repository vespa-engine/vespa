// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fusion_input_index.h"
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

using search::index::SchemaUtil;
using vespalib::IllegalArgumentException;
using vespalib::make_string;

namespace search::diskindex {

FusionInputIndex::FusionInputIndex(const vespalib::string& path, uint32_t index, const SelectorArray& selector)
    : _path(path),
      _index(index),
      _selector(&selector),
      _schema(),
      _docIdMapping()
{
}

FusionInputIndex::~FusionInputIndex() = default;

void
FusionInputIndex::setup()
{
    vespalib::string fname = _path + "/schema.txt";
    if ( ! _schema.loadFromFile(fname)) {
        throw IllegalArgumentException(make_string("Failed loading schema %s", fname.c_str()));
    }
    if ( ! SchemaUtil::validateSchema(_schema)) {
        throw IllegalArgumentException(make_string("Failed validating schema %s", fname.c_str()));
    }
    if (!_docIdMapping.readDocIdLimit(_path)) {
        throw IllegalArgumentException(make_string("Cannot determine docIdLimit for old index \"%s\"", _path.c_str()));
    }
    _docIdMapping.setup(_docIdMapping._docIdLimit, _selector, _index);
}

}
