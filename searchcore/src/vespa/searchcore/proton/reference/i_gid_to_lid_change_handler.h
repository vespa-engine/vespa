// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <set>
#include <memory>
#include <vespa/vespalib/stllike/string.h>

namespace document { class GlobalId; }

namespace proton {

class IGidToLidChangeListener;

/*
 * Interface class for registering listeners that get notification when
 * gid to lid mapping changes.
 */
class IGidToLidChangeHandler
{
public:
    virtual ~IGidToLidChangeHandler() { }

    virtual void addListener(std::unique_ptr<IGidToLidChangeListener> listener) = 0;

    virtual void removeListeners(const vespalib::string &docTypeName,
                                 const std::set<vespalib::string> &keepNames) = 0;
    virtual void notifyGidToLidChange(document::GlobalId gid, uint32_t lid)  = 0;

};

} // namespace proton
