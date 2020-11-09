// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search { class AttributeVector; }
namespace document { class Field; }

namespace proton {

struct IFieldUpdateCallback {
    virtual ~IFieldUpdateCallback() = default;
    virtual void onUpdateField(const document::Field & field, const search::AttributeVector * attr) = 0;
};

struct DummyFieldUpdateCallback : IFieldUpdateCallback {
    void onUpdateField(const document::Field & , const search::AttributeVector *) override {}
};

}
