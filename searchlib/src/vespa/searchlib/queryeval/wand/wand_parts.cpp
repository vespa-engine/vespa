// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wand_parts.h"
#include <vespa/vespalib/objects/visit.hpp>

namespace search::queryeval::wand {

void
VectorizedIteratorTerms::visit_members(vespalib::ObjectVisitor &visitor) const {
    visit(visitor, "children", _terms);
}

VectorizedIteratorTerms::VectorizedIteratorTerms(VectorizedIteratorTerms &&) noexcept = default;
VectorizedIteratorTerms & VectorizedIteratorTerms::operator=(VectorizedIteratorTerms &&) noexcept = default;
VectorizedIteratorTerms::~VectorizedIteratorTerms() = default;

}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::wand::Term &obj)
{
    self.openStruct(name, "search::queryeval::wand::Term");
    visit(self, "weight",  obj.weight);
    visit(self, "estHits", obj.estHits);
    visit(self, "search",  obj.search);
    self.closeStruct();
}
