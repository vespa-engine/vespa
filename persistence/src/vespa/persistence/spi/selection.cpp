// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "selection.h"

namespace storage::spi {

Selection::Selection(const DocumentSelection& docSel)
    : _documentSelection(docSel),
      _fromTimestamp(0),
      _toTimestamp(INT64_MAX),
      _timestampSubset()
{ }

Selection::~Selection() = default;

}

