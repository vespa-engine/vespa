// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentlocations.h"
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributevector.h>

namespace search::common {

DocumentLocations::DocumentLocations()
    : _vec(nullptr)
{
}

DocumentLocations::~DocumentLocations() = default;

DocumentLocations::DocumentLocations(DocumentLocations &&) = default;
DocumentLocations & DocumentLocations::operator = (DocumentLocations &&) = default;


}
