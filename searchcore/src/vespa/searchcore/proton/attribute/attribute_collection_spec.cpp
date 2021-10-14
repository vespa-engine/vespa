// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_collection_spec.h"

namespace proton {

AttributeCollectionSpec::AttributeCollectionSpec(const AttributeList &attributes,
                                                 uint32_t docIdLimit,
                                                 SerialNum currentSerialNum)
    : _attributes(attributes),
      _docIdLimit(docIdLimit),
      _currentSerialNum(currentSerialNum)
{
}

AttributeCollectionSpec::~AttributeCollectionSpec() { }

}
