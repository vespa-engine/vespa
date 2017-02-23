// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_gid_to_lid_change_handler.h"
#include <vector>
#include <mutex>

namespace document { class GlobalId; }
namespace searchcorespi { namespace index { class IThreadService; } }

namespace proton {

/*
 * Class for registering listeners that get notification when
 * gid to lid mapping changes. Also handles notifications which have
 * to be executed by master thread service.
 */
class GidToLidChangeHandler : public std::enable_shared_from_this<GidToLidChangeHandler>,
                              public IGidToLidChangeHandler
{
    using lock_guard = std::lock_guard<std::mutex>;
    std::mutex _lock;
    std::vector<std::unique_ptr<IGidToLidChangeListener>> _listeners;
    searchcorespi::index::IThreadService *_master;

    void performAddListener(std::unique_ptr<IGidToLidChangeListener> listener);
    void performRemoveListener(const vespalib::string &docTypeName,
                               const std::set<vespalib::string> &keepNames);
public:
    GidToLidChangeHandler(searchcorespi::index::IThreadService *master);
    virtual ~GidToLidChangeHandler();

    /**
     * Notify gid to lid mapping change. Called by master executor.
     */
    void notifyGidToLidChange(document::GlobalId gid, uint32_t lid);

    /**
     * Close handler, further notifications are blocked.  Called by master
     * executor.
     */
    void close();

    /*
     * Add listener unless a listener with matching docTypeName and
     * name already exists.
     */
    virtual void addListener(std::unique_ptr<IGidToLidChangeListener> listener) override;

    /**
     * Remove listeners with matching docTypeName unless name is present in
     * keepNames.
     */
    virtual void removeListeners(const vespalib::string &docTypeName,
                                 const std::set<vespalib::string> &keepNames) override;
};

} // namespace proton
