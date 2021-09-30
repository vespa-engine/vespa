// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/vespalib/stllike/string.h>

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
    virtual ~IGidToLidChangeListener() { }
    virtual void notifyPutDone(IDestructorCallbackSP context, document::GlobalId gid, uint32_t lid) = 0;
    virtual void notifyRemove(IDestructorCallbackSP context, document::GlobalId gid) = 0;
    virtual void notifyRegistered() = 0;
    virtual const vespalib::string &getName() const = 0;
    virtual const vespalib::string &getDocTypeName() const = 0;
};

} // namespace proton
