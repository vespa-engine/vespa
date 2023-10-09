// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "isourceselector.h"

namespace search::queryeval {

ISourceSelector::ISourceSelector(Source defaultSource) :
    _baseId(0),
    _defaultSource(defaultSource)
{
    assert(defaultSource < SOURCE_LIMIT);
}

void
ISourceSelector::setDefaultSource(Source source)
{
    assert(source < SOURCE_LIMIT);
    assert(source >= _defaultSource);
    _defaultSource = source;
}

}
