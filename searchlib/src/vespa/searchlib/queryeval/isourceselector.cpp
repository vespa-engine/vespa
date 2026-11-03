// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "isourceselector.h"

#include <vespa/check_require.h>

namespace search::queryeval {

ISourceSelector::ISourceSelector(Source defaultSource) : _baseId(0), _defaultSource(defaultSource) {
    CHECK(defaultSource < SOURCE_LIMIT);
}

void ISourceSelector::setDefaultSource(Source source) {
    CHECK(source < SOURCE_LIMIT);
    CHECK(source >= _defaultSource);
    _defaultSource = source;
}

} // namespace search::queryeval
