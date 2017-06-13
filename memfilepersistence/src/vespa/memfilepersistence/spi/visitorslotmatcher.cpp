// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitorslotmatcher.h"
#include <vespa/document/select/bodyfielddetector.h>
#include <vespa/document/select/node.h>

namespace storage {
namespace memfile {

namespace {

SlotMatcher::PreloadFlag
getCacheRequirements(const document::select::Node* selection,
                     const document::DocumentTypeRepo& repo) {
    if (!selection) {
        return SlotMatcher::PRELOAD_META_DATA_ONLY;
    }

    document::select::BodyFieldDetector detector(repo);
    selection->visit(detector);

    if (detector.foundBodyField) {
        return SlotMatcher::PRELOAD_BODY;
    } else {
        return SlotMatcher::PRELOAD_HEADER;
    }
}

bool needDocument(const document::select::Node* selection)
{
    if (selection) {
        document::select::NeedDocumentDetector detector;
        selection->visit(detector);
        return detector.needDocument();
    } else {
        return false;
    }
}

}  // namespace

VisitorSlotMatcher::VisitorSlotMatcher(
        const document::DocumentTypeRepo& repo,
        const document::select::Node* selection)
    : SlotMatcher(getCacheRequirements(selection, repo)),
      _selection(selection),
      _needDocument(needDocument(selection))
{
}

bool
VisitorSlotMatcher::match(const Slot& slot) {
    if (_selection) {
        if (!slot.isRemove() && _needDocument) {
            document::Document::UP doc(
                    slot.getDocument(!(_preload == PRELOAD_BODY)));
            return (_selection->contains(*doc)
                    == document::select::Result::True);
        } else {
            document::DocumentId docId(slot.getDocumentId());
            return (_selection->contains(docId)
                    == document::select::Result::True);
        }
    }

    return true;
}

}
}
