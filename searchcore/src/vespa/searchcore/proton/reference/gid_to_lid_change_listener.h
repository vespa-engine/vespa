// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_gid_to_lid_change_listener.h"
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/common/sequencedtaskexecutor.h>
#include <vespa/searchcore/proton/common/monitored_refcount.h>

namespace proton {

/*
 * Class for listening to changes in mapping from gid to lid and updating
 * reference attribute appropriately.
 */
class GidToLidChangeListener : public IGidToLidChangeListener
{
    search::ISequencedTaskExecutor                        &_attributeFieldWriter;
    search::ISequencedTaskExecutor::ExecutorId             _executorId;
    std::shared_ptr<search::attribute::ReferenceAttribute> _attr;
    MonitoredRefCount                                     &_refCount;
    vespalib::string                                       _name;
    vespalib::string                                       _docTypeName;

public:
    GidToLidChangeListener(search::ISequencedTaskExecutor &attributeFieldWriter,
                           std::shared_ptr<search::attribute::ReferenceAttribute> attr,
                           MonitoredRefCount &refCount,
                           const vespalib::string &name,
                           const vespalib::string &docTypeName);
    virtual ~GidToLidChangeListener();
    virtual void notifyPutDone(document::GlobalId gid, uint32_t lid) override;
    virtual void notifyRemove(document::GlobalId gid) override;
    virtual void notifyRegistered() override;
    virtual const vespalib::string &getName() const override;
    virtual const vespalib::string &getDocTypeName() const override;
    const std::shared_ptr<search::attribute::ReferenceAttribute> &getReferenceAttribute() const { return _attr; }
};

} // namespace proton
