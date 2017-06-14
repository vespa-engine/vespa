// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_factory.h"
#include <vespa/searchlib/attribute/attributefactory.h>

namespace proton {

using search::AttributeVector;

AttributeFactory::AttributeFactory()
{
}

AttributeVector::SP
AttributeFactory::create(const vespalib::string &name,
                         const search::attribute::Config &cfg) const
{
    AttributeVector::SP v(search::AttributeFactory::createAttribute(name, cfg));
    v->enableEnumeratedSave(true);
    return v;
}

void
AttributeFactory::setupEmpty(const AttributeVector::SP &vec,
                             search::SerialNum serialNum) const
{
    vec->setCreateSerialNum(serialNum);
    vec->addReservedDoc();
}

}
