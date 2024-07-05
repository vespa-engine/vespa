// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "elementiterator.h"
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
    int prevId(-1);
    for (auto element : _tfmd) {
        uint32_t id(element.getElementId());
        if (prevId != int(id)) {
            elementIds.push_back(id);
            prevId = id;
        }
    }
}

void
ElementIteratorWrapper::mergeElementIds(uint32_t docId, std::vector<uint32_t> & elementIds) {
    _search->unpack(docId);
    size_t toKeep(0);
    int32_t id(-1);
    auto it = _tfmd.begin();
    for (int32_t candidate : elementIds) {
        if (candidate > id) {
            while ((it < _tfmd.end()) && (candidate > int(it->getElementId()))) {
                it++;
            }
            if (it == _tfmd.end()) break;
            id = it->getElementId();
        }
        if (id == candidate) {
            elementIds[toKeep++] = candidate;
        }
    }
    elementIds.resize(toKeep);
}

}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::ElementIterator *obj)
{
    if (obj != 0) {
        self.openStruct(name, "ElementIterator");
        obj->visitMembers(self);
        self.closeStruct();
    } else {
        self.visitNull(name);
    }
}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::ElementIterator &obj)
{
    visit(self, name, &obj);
}
