// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "messages50test.h"

class Messages51Test : public Messages50Test {
protected:
    const vespalib::Version getVersion() const override { return vespalib::Version(5, 1); }
    bool shouldTestCoverage() const override { return TRUE; }

public:
    Messages51Test();

    bool testCreateVisitorMessage();
    bool testGetDocumentMessage();
    bool testDocumentIgnoredReply();
};

