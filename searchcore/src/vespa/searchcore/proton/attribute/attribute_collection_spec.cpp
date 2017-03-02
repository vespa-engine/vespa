// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_collection_spec.h"

namespace proton {

AttributeCollectionSpec::Attribute::Attribute(const vespalib::string &name,
                                              const search::attribute::Config &cfg)
    : _name(name),
      _cfg(cfg)
{
}

AttributeCollectionSpec::Attribute::Attribute(const Attribute &) = default;

AttributeCollectionSpec::Attribute &
AttributeCollectionSpec::Attribute::operator=(const Attribute &) = default;

AttributeCollectionSpec::Attribute::Attribute(Attribute &&) = default;

AttributeCollectionSpec::Attribute &
AttributeCollectionSpec::Attribute::operator=(Attribute &&) = default;

AttributeCollectionSpec::Attribute::~Attribute() { }

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
