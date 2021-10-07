// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "namedobject.h"

namespace vespalib {

IMPLEMENT_IDENTIFIABLE_NS(vespalib, NamedObject, Identifiable);


Serializer & NamedObject::onSerialize(Serializer & os) const
{
    return os.put(_name);
}

Deserializer & NamedObject::onDeserialize(Deserializer & is)
{
    return is.get(_name);
}

}
