// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/common/sequencedtaskexecutor.h>
#include <vespa/searchcore/proton/common/monitored_refcount.h>


namespace proton {

/*
 * Class for listening to changes in mapping from gid to lid and updating
 * reference attribute appropriately.
 */
class GidToLidChangeListener
{
    search::ISequencedTaskExecutor &_attributeFieldWriter;
    std::shared_ptr<search::attribute::ReferenceAttribute> _attr;
    MonitoredRefCount              &_refCount;

public:
    GidToLidChangeListener(search::ISequencedTaskExecutor &attributeFieldWriter,
                           std::shared_ptr<search::attribute::ReferenceAttribute> attr,
                           MonitoredRefCount &refCount);
    virtual ~GidToLidChangeListener();
    void notifyGidToLidChange(document::GlobalId gid, uint32_t lid);
};

} // namespace proton
