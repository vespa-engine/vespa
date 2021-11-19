// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_gid_to_lid_change_listener.h"
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/vespalib/util/retain_guard.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

namespace proton {

/*
 * Class for listening to changes in mapping from gid to lid and updating
 * reference attribute appropriately.
 */
class GidToLidChangeListener : public IGidToLidChangeListener
{
    vespalib::ISequencedTaskExecutor                      &_executor;
    vespalib::ISequencedTaskExecutor::ExecutorId           _executorId;
    std::shared_ptr<search::attribute::ReferenceAttribute> _attr;
    vespalib::RetainGuard                                  _retainGuard;
    vespalib::string                                       _name;
    vespalib::string                                       _docTypeName;

public:
    GidToLidChangeListener(vespalib::ISequencedTaskExecutor &attributeFieldWriter,
                           std::shared_ptr<search::attribute::ReferenceAttribute> attr,
                           vespalib::RetainGuard refCount,
                           const vespalib::string &name,
                           const vespalib::string &docTypeName);
    ~GidToLidChangeListener() override;
    void notifyPutDone(IDestructorCallbackSP context, document::GlobalId gid, uint32_t lid) override;
    void notifyRemove(IDestructorCallbackSP context, document::GlobalId gid) override;
    void notifyRegistered(const std::vector<document::GlobalId>& removes) override;
    const vespalib::string &getName() const override;
    const vespalib::string &getDocTypeName() const override;
    const std::shared_ptr<search::attribute::ReferenceAttribute> &getReferenceAttribute() const { return _attr; }
};

} // namespace proton
