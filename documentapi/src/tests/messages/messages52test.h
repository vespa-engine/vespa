// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell
#pragma once

#include "messages51test.h"

class Messages52Test : public Messages51Test {
protected:
    const vespalib::Version getVersion() const override { return vespalib::Version(5, 115, 0); }

public:
    Messages52Test();

    bool testPutDocumentMessage();
    bool testUpdateDocumentMessage();
    bool testRemoveDocumentMessage();

private:
    static size_t serializedLength(const string & str) {
        return sizeof(int32_t) + str.size();
    }
};

