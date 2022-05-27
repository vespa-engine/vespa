// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "not_implemented_attribute.h"
#include "search_context.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/classname.h>

using vespalib::make_string_short::fmt;
using vespalib::getClassName;

namespace search {

using largeint_t = attribute::IAttributeVector::largeint_t;
using attribute::SearchContext;

NotImplementedAttribute::NotImplementedAttribute(const vespalib::string &name)
    : NotImplementedAttribute(name, Config())
{}

NotImplementedAttribute::NotImplementedAttribute(const vespalib::string &name, const Config & cfg)
    : AttributeVector(name, cfg)
{}

void
NotImplementedAttribute::notImplemented() const {
    throw vespalib::UnsupportedOperationException(fmt("The function is not implemented for attribute '%s' of type '%s'.",
                                                      getName().c_str(), getClassName(*this).c_str()));
}

uint32_t
NotImplementedAttribute::getValueCount(DocId) const {
    notImplemented();
}

largeint_t
NotImplementedAttribute::getInt(DocId) const {
    notImplemented();
}

double
NotImplementedAttribute::getFloat(DocId) const {
    notImplemented();
}

const char *
NotImplementedAttribute::getString(DocId, char *, size_t) const {
    notImplemented();
}

uint32_t
NotImplementedAttribute::get(DocId, largeint_t *, uint32_t) const {
    notImplemented();
}

uint32_t
NotImplementedAttribute::get(DocId, double *, uint32_t) const {
    notImplemented();
}

uint32_t
NotImplementedAttribute::get(DocId, vespalib::string *, uint32_t) const {
    notImplemented();
}

uint32_t
NotImplementedAttribute::get(DocId, const char **, uint32_t) const {
    notImplemented();
}

uint32_t
NotImplementedAttribute::get(DocId, EnumHandle *, uint32_t) const {
    notImplemented();
}

uint32_t
NotImplementedAttribute::get(DocId, WeightedInt *, uint32_t) const {
    notImplemented();
}

uint32_t
NotImplementedAttribute::get(DocId, WeightedFloat *, uint32_t) const {
    notImplemented();
}

uint32_t
NotImplementedAttribute::get(DocId, WeightedString *, uint32_t) const {
    notImplemented();
}

uint32_t
NotImplementedAttribute::get(DocId, WeightedConstChar *, uint32_t) const {
    notImplemented();
}

uint32_t
NotImplementedAttribute::get(DocId, WeightedEnum *, uint32_t) const {
    notImplemented();
}

bool
NotImplementedAttribute::findEnum(const char *, EnumHandle &) const {
    notImplemented();
}

std::vector<NotImplementedAttribute::EnumHandle>
NotImplementedAttribute::findFoldedEnums(const char *) const {
    notImplemented();
}

long
NotImplementedAttribute::onSerializeForAscendingSort(DocId, void *, long, const common::BlobConverter *) const {
    notImplemented();
}

long
NotImplementedAttribute::onSerializeForDescendingSort(DocId, void *, long, const common::BlobConverter *) const {
    notImplemented();
}

uint32_t
NotImplementedAttribute::clearDoc(DocId) {
    notImplemented();
}

uint32_t
NotImplementedAttribute::getEnum(DocId) const {
    notImplemented();
}

bool
NotImplementedAttribute::addDoc(DocId &) {
    notImplemented();
}

std::unique_ptr<SearchContext>
NotImplementedAttribute::getSearch(QueryTermSimpleUP, const attribute::SearchContextParams &) const {
    notImplemented();
}

void NotImplementedAttribute::onAddDocs(DocId ) { }
    
}
