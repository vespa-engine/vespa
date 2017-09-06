// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <set>
#include <memory>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/common/serialnum.h>

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
    using SerialNum = search::SerialNum;
    using GlobalId = document::GlobalId;

    virtual ~IGidToLidChangeHandler() { }

    /*
     * Add listener unless a listener with matching docTypeName and
     * name already exists.
     */
    virtual void addListener(std::unique_ptr<IGidToLidChangeListener> listener) = 0;

    /**
     * Remove listeners with matching docTypeName unless name is present in
     * keepNames.
     */
    virtual void removeListeners(const vespalib::string &docTypeName,
                                 const std::set<vespalib::string> &keepNames) = 0;
    /**
     * Notify gid to lid mapping change.
     */
    virtual void notifyPut(GlobalId gid, uint32_t lid, SerialNum serialNum) = 0;
    virtual void notifyRemove(GlobalId gid, SerialNum serialNum) = 0;
    virtual void notifyRemoveDone(GlobalId gid, SerialNum serialNum) = 0;
};

} // namespace proton
