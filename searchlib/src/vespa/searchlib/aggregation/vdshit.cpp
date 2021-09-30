// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vdshit.h"
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/objects/serializer.hpp>
#include <vespa/vespalib/objects/deserializer.hpp>

namespace search::aggregation {

using vespalib::Serializer;
using vespalib::Deserializer;

namespace {
    vespalib::string _G_docIdField("docId");
    vespalib::string _G_summaryField("summary");
}

IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, VdsHit, Hit);

VdsHit::~VdsHit() = default;

Serializer &
VdsHit::onSerialize(Serializer &os) const
{
    Hit::onSerialize(os);
    return os.put(_docId) << _summary;
}

Deserializer &
VdsHit::onDeserialize(Deserializer &is)
{
    Hit::onDeserialize(is);
    return is.get(_docId) >> _summary;
}

void
VdsHit::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    Hit::visitMembers(visitor);
    visit(visitor, _G_docIdField, _docId);
    visit(visitor, _G_summaryField, _summary);
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_vdshit() {}
