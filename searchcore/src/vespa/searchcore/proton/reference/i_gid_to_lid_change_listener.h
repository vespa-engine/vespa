// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/idestructorcallback.h>
#include <string>
#include <vector>

namespace document { class GlobalId; }

namespace proton {

/*
 * Interface class for listening to changes in mapping from gid to lid
 * and updating reference attribute appropriately.
 */
class IGidToLidChangeListener
{
public:
    using IDestructorCallbackSP = std::shared_ptr<vespalib::IDestructorCallback>;
    virtual ~IGidToLidChangeListener() = default;
    virtual void notifyPutDone(IDestructorCallbackSP context, document::GlobalId gid, uint32_t lid) = 0;
    virtual void notifyRemove(IDestructorCallbackSP context, document::GlobalId gid) = 0;
    virtual void notifyRegistered(const std::vector<document::GlobalId>& removes) = 0;
    virtual const std::string &getName() const = 0;
    virtual const std::string &getDocTypeName() const = 0;
};

} // namespace proton
