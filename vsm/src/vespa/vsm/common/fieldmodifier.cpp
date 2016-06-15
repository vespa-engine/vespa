// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vsm/common/fieldmodifier.h>

namespace vsm
{
FieldModifierMap::FieldModifierMap() :
    _map()
{
}

FieldModifier *
FieldModifierMap::getModifier(FieldIdT fId) const
{
    FieldModifierMapT::const_iterator itr = _map.find(fId);
    if (itr == _map.end()) {
        return NULL;
    }
    return itr->second.get();
}


}

