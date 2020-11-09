// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search { class AttributeVector; }

namespace proton {

struct IFieldUpdateCallback {
    virtual ~IFieldUpdateCallback() { }
    virtual void onUpdateField(vespalib::stringref fieldName, const search::AttributeVector * attr) = 0;
};

struct DummyFieldUpdateCallback : IFieldUpdateCallback {
    void onUpdateField(vespalib::stringref, const search::AttributeVector *) override {}
};

}
