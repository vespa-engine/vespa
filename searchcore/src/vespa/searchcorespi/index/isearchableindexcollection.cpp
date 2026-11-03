// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "isearchableindexcollection.h"

#include <vespa/check_require.h>
#include <vespa/searchlib/queryeval/isourceselector.h>

namespace searchcorespi {

using search::queryeval::ISourceSelector;

void ISearchableIndexCollection::setCurrentIndex(uint32_t id) {
    CHECK(id < ISourceSelector::SOURCE_LIMIT);

    _currentIndex = id;
}

uint32_t ISearchableIndexCollection::getCurrentIndex() const {
    CHECK(valid());

    return _currentIndex;
}

bool ISearchableIndexCollection::valid() const {
    return (_currentIndex > 0) && (_currentIndex < ISourceSelector::SOURCE_LIMIT);
}

} // namespace searchcorespi
