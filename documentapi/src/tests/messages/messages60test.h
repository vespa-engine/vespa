// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "messages52test.h"

class Messages60Test : public Messages52Test {
protected:
    // TODO finalize version
    const vespalib::Version getVersion() const override { return vespalib::Version(6, 999, 0); }
public:
    Messages60Test();
    bool testCreateVisitorMessage();
};
