// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentlocations.h"
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributevector.h>

namespace search {
namespace common {

DocumentLocations::DocumentLocations()
    : _vec_guard(new AttributeGuard),
      _vec(NULL)
{
}

DocumentLocations::~DocumentLocations() { }

void
DocumentLocations::setVecGuard(std::unique_ptr<search::AttributeGuard> guard) {
    _vec_guard = std::move(guard);
    setVec(*_vec_guard.get()->get());
}

DocumentLocations::DocumentLocations(DocumentLocations &&) = default;
DocumentLocations & DocumentLocations::operator = (DocumentLocations &&) = default;


}  // namespace common
}  // namespace search
