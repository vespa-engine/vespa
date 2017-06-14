// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "namedobject.h"

namespace vespalib {

IMPLEMENT_IDENTIFIABLE_NS(vespalib, NamedObject, Identifiable);

static FieldBase _G_nameField("name");

Serializer & NamedObject::onSerialize(Serializer & os) const
{
    return os.put(_G_nameField, _name);
}

Deserializer & NamedObject::onDeserialize(Deserializer & is)
{
    return is.get(_G_nameField, _name);
}

}
