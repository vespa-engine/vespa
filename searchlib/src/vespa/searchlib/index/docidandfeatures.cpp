// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docidandfeatures.h"
#include <vespa/log/log.h>
LOG_SETUP(".index.docidandfeatures");

namespace search::index {

DocIdAndFeatures::DocIdAndFeatures()
    : _docId(0),
      _elements(),
      _wordPositions(),
      _blob(),
      _bitOffset(0u),
      _bitLength(0u),
      _raw(false)
{ }

DocIdAndFeatures::DocIdAndFeatures(const DocIdAndFeatures &) = default;
DocIdAndFeatures & DocIdAndFeatures::operator = (const DocIdAndFeatures &) = default;
DocIdAndFeatures::~DocIdAndFeatures() { }

}
