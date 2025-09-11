// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_vector_wrapper.h"
#include <vespa/searchlib/attribute/attributevector.h>

#include <chrono>

namespace proton {

AttributeVectorWrapper::AttributeVectorWrapper(const std::string &name) :
    _name(name)
    {
}

void AttributeVectorWrapper::setAttributeVector(const std::shared_ptr<search::AttributeVector> &attr) {
    std::unique_lock<std::shared_mutex> guard(_mutex);
    _attr = attr;
}

std::shared_ptr<search::AttributeVector> AttributeVectorWrapper::getAttributeVector() const {
    std::unique_lock<std::shared_mutex> guard(_mutex);
    return _attr;
}

}
