// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "elementiterator.h"
#include "element_id_extractor.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/objectvisitor.h>

namespace search::queryeval {

void
ElementIterator::visitMembers(vespalib::ObjectVisitor &visitor) const {
    visit(visitor, "iterator", _search.get());
}

ElementIteratorWrapper::ElementIteratorWrapper(SearchIterator::UP search, fef::TermFieldMatchData & tfmd)
    : ElementIterator(std::move(search)),
      _tfmd(tfmd)
{}

ElementIteratorWrapper::~ElementIteratorWrapper() = default;

void
ElementIteratorWrapper::getElementIds(uint32_t docId, std::vector<uint32_t> & elementIds) {
    _search->unpack(docId);
    ElementIdExtractor::get_element_ids(_tfmd, docId, elementIds);
}

void
ElementIteratorWrapper::mergeElementIds(uint32_t docId, std::vector<uint32_t> & elementIds) {
    _search->unpack(docId);
    ElementIdExtractor::and_element_ids_into(_tfmd, docId, elementIds);
}

}

void visit(vespalib::ObjectVisitor &self, std::string_view name,
           const search::queryeval::ElementIterator *obj)
{
    if (obj != nullptr) {
        self.openStruct(name, "ElementIterator");
        obj->visitMembers(self);
        self.closeStruct();
    } else {
        self.visitNull(name);
    }
}

void visit(vespalib::ObjectVisitor &self, std::string_view name,
           const search::queryeval::ElementIterator &obj)
{
    visit(self, name, &obj);
}
