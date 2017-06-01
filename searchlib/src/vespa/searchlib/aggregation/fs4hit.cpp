// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fs4hit.h"
#include <vespa/vespalib/objects/visit.h>

namespace search {
namespace aggregation {

using vespalib::FieldBase;
using vespalib::Serializer;
using vespalib::Deserializer;

static FieldBase _G_pathField("path");
static FieldBase _G_docIdField("docId");
static FieldBase _G_globalIdField("globalId");
static FieldBase _G_distributionKeyField("distributionKey");

IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, FS4Hit, Hit);

Serializer &
FS4Hit::onSerialize(Serializer &os) const
{
    Hit::onSerialize(os);
    os.put(_G_pathField, _path);
    const unsigned char * rawGid = _globalId.get();
    for (size_t i = 0; i < document::GlobalId::LENGTH; ++i) {
        os.put(_G_globalIdField, rawGid[i]);
    }
    os.put(_G_distributionKeyField, _distributionKey);
    return os;
}

Deserializer &
FS4Hit::onDeserialize(Deserializer &is)
{
    Hit::onDeserialize(is);
    is.get(_G_pathField, _path);
    unsigned char rawGid[document::GlobalId::LENGTH];
    for (size_t i = 0; i < document::GlobalId::LENGTH; ++i) {
        is.get(_G_globalIdField, rawGid[i]);
    }
    _globalId.set(rawGid);
    is.get(_G_distributionKeyField, _distributionKey);
    return is;
}

void
FS4Hit::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    Hit::visitMembers(visitor);
    visit(visitor, _G_pathField.getName(), _path);
    visit(visitor, _G_docIdField.getName(), _docId);
    visit(visitor, _G_globalIdField.getName(), _globalId.toString());
    visit(visitor, _G_distributionKeyField.getName(), _distributionKey);
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_fs4hit() {}
