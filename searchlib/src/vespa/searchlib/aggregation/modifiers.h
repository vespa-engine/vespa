// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/objectpredicate.h>

namespace search {
namespace aggregation {

class Attribute2DocumentAccessor : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
{
private:
    void execute(vespalib::Identifiable &obj) override;
    bool check(const vespalib::Identifiable &obj) const override;
};

}
}

