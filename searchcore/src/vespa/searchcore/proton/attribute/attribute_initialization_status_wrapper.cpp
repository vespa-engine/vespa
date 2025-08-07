// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initialization_status_wrapper.h"

#include <chrono>

namespace proton {

AttributeInitializationStatusWrapper::AttributeInitializationStatusWrapper(const std::string &name) :
    _name(name)
    {
}

void AttributeInitializationStatusWrapper::setAttributeVector(const search::AttributeVector::SP &attr) {
    std::unique_lock<std::shared_mutex> guard(_mutex);
    _attr = attr;
}

search::AttributeVector::SP AttributeInitializationStatusWrapper::getAttributeVector() const {
    std::unique_lock<std::shared_mutex> guard(_mutex);
    return _attr;
}

}
