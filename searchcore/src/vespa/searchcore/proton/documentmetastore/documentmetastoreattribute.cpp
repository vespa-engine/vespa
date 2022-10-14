// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastoreattribute.h"
#include <vespa/searchcommon/attribute/config.h>

namespace proton {

namespace {

const vespalib::string documentMetaStoreName("[documentmetastore]");

}

const vespalib::string &
DocumentMetaStoreAttribute::getFixedName()
{
    return documentMetaStoreName;
}

DocumentMetaStoreAttribute::DocumentMetaStoreAttribute(const vespalib::string &name)
    : NotImplementedAttribute(name, Config(BasicType::NONE))
{ }


DocumentMetaStoreAttribute::~DocumentMetaStoreAttribute() = default;

}
