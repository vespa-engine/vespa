// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "messages52test.h"

class Messages60Test : public Messages52Test {
protected:
    const vespalib::Version getVersion() const override { return vespalib::Version(6, 221); }
public:
    Messages60Test();
    bool testCreateVisitorMessage();
    bool testStatBucketMessage();
    bool testGetBucketListMessage();
};
