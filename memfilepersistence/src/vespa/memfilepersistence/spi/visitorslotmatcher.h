// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/memfilepersistence/common/slotmatcher.h>

namespace document {
    namespace select {
        class Node;
    }
}

namespace storage {
namespace memfile {

class VisitorSlotMatcher : public SlotMatcher
{
private:
    const document::select::Node* _selection;
    bool _needDocument;

public:
    VisitorSlotMatcher(const document::DocumentTypeRepo& repo,
                       const document::select::Node* selection);

    bool match(const Slot& slot) override;

};

}
}

