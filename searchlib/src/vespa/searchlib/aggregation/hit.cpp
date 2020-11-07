// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "hit.h"
#include <vespa/vespalib/objects/visit.h>

namespace search::aggregation {

using vespalib::Serializer;
using vespalib::Deserializer;

IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS2(search, aggregation, Hit, vespalib::Identifiable);

namespace {
    const vespalib::string _G_rankField("rank");
}

Serializer &
Hit::onSerialize(Serializer &os) const
{
    return os.put(_rank);
}

Deserializer &
Hit::onDeserialize(Deserializer &is)
{
    return is.get(_rank);
}

int
Hit::onCmp(const Identifiable &b) const
{
    const Hit &h = (const Hit &)b;
    return (_rank > h._rank) ? -1 : ((_rank < h._rank) ? 1 : 0);
}

void
Hit::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, _G_rankField, _rank);
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_hit() {}
