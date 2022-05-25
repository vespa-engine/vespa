// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "floatbase.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/searchcommon/attribute/config.h>

namespace search {

template<typename T>
FloatingPointAttributeTemplate<T>::FloatingPointAttributeTemplate(const vespalib::string & name)
    : FloatingPointAttributeTemplate(name, BasicType::fromType(T()))
{ }

template<typename T>
FloatingPointAttributeTemplate<T>::FloatingPointAttributeTemplate(const vespalib::string & name, const Config & c)
    : FloatingPointAttribute(name, c),
      _defaultValue(ChangeBase::UPDATE, 0, defaultValue())
{
    assert(c.basicType() == BasicType::fromType(T()));
}

template<typename T>
FloatingPointAttributeTemplate<T>::~FloatingPointAttributeTemplate() = default;

template<typename T>
bool
FloatingPointAttributeTemplate<T>::findEnum(const char *value, EnumHandle &e) const {
    vespalib::asciistream iss(value);
    T fvalue = 0;
    try {
        iss >> fvalue;
    } catch (const vespalib::IllegalArgumentException &) {
    }
    return findEnum(fvalue, e);
}

template<typename T>
std::vector<IEnumStore::EnumHandle>
FloatingPointAttributeTemplate<T>::findFoldedEnums(const char *value) const
{
    std::vector<EnumHandle> result;
    EnumHandle h;
    if (findEnum(value, h)) {
        result.push_back(h);
    }
    return result;
}

template<typename T>
bool
FloatingPointAttributeTemplate<T>::isUndefined(DocId doc) const {
    return attribute::isUndefined(get(doc));
}

template<typename T>
long
FloatingPointAttributeTemplate<T>::onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const {
    (void) bc;
    if (available >= long(sizeof(T))) {
        T origValue(get(doc));
        vespalib::serializeForSort< vespalib::convertForSort<T, true> >(origValue, serTo);
    } else {
        return -1;
    }
    return sizeof(T);
}

template<typename T>
long
FloatingPointAttributeTemplate<T>::onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const {
    (void) bc;
    if (available >= long(sizeof(T))) {
        T origValue(get(doc));
        vespalib::serializeForSort< vespalib::convertForSort<T, false> >(origValue, serTo);
    } else {
        return -1;
    }
    return sizeof(T);
}

}
