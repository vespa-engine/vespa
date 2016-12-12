// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "writabledocumentlist.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/update/documentupdate.h>

#include <vespa/log/log.h>

LOG_SETUP(".vdslib.container.writabledocumentlist");

namespace vdslib {
WritableDocumentList::WritableDocumentList(const document::DocumentTypeRepo::SP & repo, char* buffer, uint32_t bufferSize, bool keepexisting)
    : MutableDocumentList(repo, buffer, bufferSize, keepexisting)
{
}

WritableDocumentList::WritableDocumentList(const DocumentList& source,
                                           char* buffer,
                                           uint32_t bufferSize)
    : MutableDocumentList(source, buffer, bufferSize)
{
}

char*
WritableDocumentList::prepareMultiput(uint32_t count, uint32_t contentSize)
{
    //printState("Prepare multiput");
    uint32_t freeSpace = countFree();
    uint32_t metaSpace = count * sizeof(MetaEntry);
    if (freeSpace < metaSpace || freeSpace - metaSpace < contentSize) {
        return 0;
    }
    return _freePtr - contentSize;
}

bool
WritableDocumentList::commitMultiput(const std::vector<MetaEntry>& meta, char* contentPtr)
{
    //printState("Pre commit multiput");
    uint32_t diff = contentPtr - _buffer;
    uint32_t oldDocCount = docCount();
    uint32_t highPos = 0;
    uint32_t nlowPos = (_freePtr - _buffer);
    for (uint32_t i=0; i<meta.size(); ++i) {
        MetaEntry& entry(getMeta(oldDocCount + i));
        entry = meta[i];
        if (entry.headerLen != 0) {
            entry.headerPos += diff;
            nlowPos = std::min(nlowPos, entry.headerPos);
            highPos = std::max(highPos, entry.headerPos + entry.headerLen);
        }
        if (entry.bodyLen != 0) {
            entry.bodyPos += diff;
            nlowPos = std::min(nlowPos, entry.bodyPos);
            highPos = std::max(highPos, entry.bodyPos + entry.bodyLen);
        }
    }
    // check for waste after written blocks
    uint32_t freePos = _freePtr - _buffer;
    if (freePos < highPos) {
        vespalib::string msg = vespalib::make_string(
                "bad multiput, reserved(%lu) < actual use(%d)",
                (_freePtr - contentPtr), (highPos - diff));
        throw vespalib::IllegalArgumentException(msg, VESPA_STRLOC);
    }
    if (freePos > highPos) {
        LOG(debug, "filling %u bytes with 0xFF", (freePos - highPos));
        memset(_buffer + highPos, 0xff, (freePos - highPos));
        _wasted += (freePos - highPos);
    }

    // Here we should have written all. Commit alterations.
    _freePtr = contentPtr;

    // check for waste before written blocks
    freePos = _freePtr - _buffer;
    if (freePos < nlowPos) {
        LOG(debug, "filling %u bytes with 0xFF", (nlowPos - freePos));
        memset(_buffer + freePos, 0xff, (nlowPos - freePos));
        _wasted += (nlowPos - freePos);
    }
    if (freePos > nlowPos) {
        vespalib::string msg = vespalib::make_string(
                "bad multiput, wrote at %p (before allocated %p)",
                _buffer + nlowPos, contentPtr);
        throw vespalib::IllegalArgumentException(msg, VESPA_STRLOC);
    }

    docCount() += meta.size();
    //printState("Post commit multiput");

    checkConsistency();

    return true;
}

} // vdslib
