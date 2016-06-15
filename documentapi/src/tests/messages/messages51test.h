// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "messages50test.h"

class Messages51Test : public Messages50Test {
protected:
    const vespalib::Version getVersion() const { return vespalib::Version(5, 1); }
    bool shouldTestCoverage() const { return TRUE; }

public:
    Messages51Test();

    bool testCreateVisitorMessage();
    bool testGetDocumentMessage();
    bool testDocumentIgnoredReply();
};

