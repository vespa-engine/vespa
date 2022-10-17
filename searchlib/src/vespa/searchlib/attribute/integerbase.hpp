// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "integerbase.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/searchcommon/attribute/config.h>

namespace search {

using largeint_t = attribute::IAttributeVector::largeint_t;

template<typename T>
IntegerAttributeTemplate<T>::IntegerAttributeTemplate(const vespalib::string & name)
    : IntegerAttributeTemplate(name, BasicType::fromType(T()))
{ }

template<typename T>
IntegerAttributeTemplate<T>::IntegerAttributeTemplate(const vespalib::string & name, const Config & c)
    : IntegerAttribute(name, c),
      _defaultValue(ChangeBase::UPDATE, 0, defaultValue())
{
    assert(c.basicType() == BasicType::fromType(T()));
}

template<typename T>
IntegerAttributeTemplate<T>::IntegerAttributeTemplate(const vespalib::string & name, const Config & c, const BasicType &realType)
    : IntegerAttribute(name, c),
      _defaultValue(ChangeBase::UPDATE, 0, 0u)
{
    assert(c.basicType() == realType);
    (void) realType;
    assert(BasicType::fromType(T()) == BasicType::INT8);
}

template<typename T>
IntegerAttributeTemplate<T>::~IntegerAttributeTemplate() = default;

template<typename T>
bool
IntegerAttributeTemplate<T>::findEnum(const char *value, EnumHandle &e) const {
    vespalib::asciistream iss(value);
    int64_t ivalue = 0;
    try {
        iss >> ivalue;
    } catch (const vespalib::IllegalArgumentException &) {
    }
    return findEnum(ivalue, e);
}


template<typename T>
std::vector<IEnumStore::EnumHandle>
IntegerAttributeTemplate<T>::findFoldedEnums(const char *value) const
{
    std::vector<EnumHandle> result;
    EnumHandle h;
    if (findEnum(value, h)) {
        result.push_back(h);
    }
    return result;
}

template<typename T>
long
IntegerAttributeTemplate<T>::onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter *) const {
    T origValue(get(doc));
    return vespalib::serializeForSort< vespalib::convertForSort<T, true> >(origValue, serTo, available);
}

template<typename T>
long
IntegerAttributeTemplate<T>::onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter *) const {
    T origValue(get(doc));
    return vespalib::serializeForSort< vespalib::convertForSort<T, false> >(origValue, serTo, available);
}

}

