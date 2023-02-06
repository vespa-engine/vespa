// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_collection_spec.h"

namespace proton {

AttributeCollectionSpec::AttributeCollectionSpec(AttributeList && attributes, uint32_t docIdLimit, SerialNum currentSerialNum)
    : _attributes(std::move(attributes)),
      _docIdLimit(docIdLimit),
      _currentSerialNum(currentSerialNum)
{
}

AttributeCollectionSpec::~AttributeCollectionSpec() = default;

bool
AttributeCollectionSpec::hasAttribute(const vespalib::string &name) const {
    for (const auto &attr : _attributes) {
        if (attr.getName() == name) {
            return true;
        }
    }
    return false;
}

}
