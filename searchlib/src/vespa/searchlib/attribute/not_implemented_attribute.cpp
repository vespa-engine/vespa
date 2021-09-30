// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "not_implemented_attribute.h"
#include <vespa/vespalib/util/exceptions.h>

using vespalib::make_string_short::fmt;
namespace search {

using largeint_t = attribute::IAttributeVector::largeint_t;
using SearchContext = AttributeVector::SearchContext;

void
NotImplementedAttribute::notImplemented() const {
    throw vespalib::IllegalStateException(fmt("The function is not implemented for attribute '%s' of type '%s'.",
                                              getName().c_str(), getNativeClassName().c_str()));
}

uint32_t
NotImplementedAttribute::getValueCount(DocId) const {
    notImplemented();
    return 0;
}

largeint_t
NotImplementedAttribute::getInt(DocId) const {
    notImplemented();
    return 0;
}

double
NotImplementedAttribute::getFloat(DocId) const {
    notImplemented();
    return 0;
}

const char *
NotImplementedAttribute::getString(DocId, char *, size_t) const {
    notImplemented();
    return NULL;
}

uint32_t
NotImplementedAttribute::get(DocId, largeint_t *, uint32_t) const {
    notImplemented();
    return 0;
}

uint32_t
NotImplementedAttribute::get(DocId, double *, uint32_t) const {
    notImplemented();
    return 0;
}

uint32_t
NotImplementedAttribute::get(DocId, vespalib::string *, uint32_t) const {
    notImplemented();
    return 0;
}

uint32_t
NotImplementedAttribute::get(DocId, const char **, uint32_t) const {
    notImplemented();
    return 0;
}

uint32_t
NotImplementedAttribute::get(DocId, EnumHandle *, uint32_t) const {
    notImplemented();
    return 0;
}

uint32_t
NotImplementedAttribute::get(DocId, WeightedInt *, uint32_t) const {
    notImplemented();
    return 0;
}

uint32_t
NotImplementedAttribute::get(DocId, WeightedFloat *, uint32_t) const {
    notImplemented();
    return 0;
}

uint32_t
NotImplementedAttribute::get(DocId, WeightedString *, uint32_t) const {
    notImplemented();
    return 0;
}

uint32_t
NotImplementedAttribute::get(DocId, WeightedConstChar *, uint32_t) const {
    notImplemented();
    return 0;
}

uint32_t
NotImplementedAttribute::get(DocId, WeightedEnum *, uint32_t) const {
    notImplemented();
    return 0;
}

bool
NotImplementedAttribute::findEnum(const char *, EnumHandle &) const {
    notImplemented();
    return false;
}

std::vector<NotImplementedAttribute::EnumHandle>
NotImplementedAttribute::findFoldedEnums(const char *) const {
    notImplemented();
    return std::vector<EnumHandle>();
}

long
NotImplementedAttribute::onSerializeForAscendingSort(DocId, void *, long, const common::BlobConverter *) const {
    notImplemented();
    return 0;
}

long
NotImplementedAttribute::onSerializeForDescendingSort(DocId, void *, long, const common::BlobConverter *) const {
    notImplemented();
    return 0;
}

uint32_t
NotImplementedAttribute::clearDoc(DocId) {
    notImplemented();
    return 0;
}

int64_t
NotImplementedAttribute::getDefaultValue() const {
    notImplemented();
    return 0;
}

uint32_t
NotImplementedAttribute::getEnum(DocId) const {
    notImplemented();
    return 0;
}

bool
NotImplementedAttribute::addDoc(DocId &) {
    notImplemented();
    return false;
}

SearchContext::UP
NotImplementedAttribute::getSearch(QueryTermSimpleUP, const attribute::SearchContextParams &) const {
    notImplemented();
    return SearchContext::UP();
}

void NotImplementedAttribute::onAddDocs(DocId ) { }
    
}
