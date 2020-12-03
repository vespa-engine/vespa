// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastoreattribute.h"
#include <vespa/vespalib/util/exceptions.h>

namespace proton {

namespace {

const vespalib::string _G_documentMetaStoreName("[documentmetastore]");

}

const vespalib::string &
DocumentMetaStoreAttribute::getFixedName()
{
    return _G_documentMetaStoreName;
}

DocumentMetaStoreAttribute::DocumentMetaStoreAttribute(const vespalib::string &name)
    : NotImplementedAttribute(name, Config(BasicType::NONE))
{ }


DocumentMetaStoreAttribute::~DocumentMetaStoreAttribute() = default;

}
