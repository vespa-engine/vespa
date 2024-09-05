// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_listener.h>
#include <vespa/searchcore/proton/reference/i_pending_gid_to_lid_changes.h>
#include <vespa/document/base/globalid.h>

namespace proton::test {

/*
 * Mockup of gid to lid change handler, used by unit tests to track
 * proper add/remove of listeners.
 */
class MockGidToLidChangeHandler : public IGidToLidChangeHandler {
public:
    using AddEntry = std::pair<std::string, std::string>;
    using RemoveEntry = std::pair<std::string, std::set<std::string>>;

private:
    std::vector<AddEntry> _adds;
    std::vector<RemoveEntry> _removes;
    std::vector<std::unique_ptr<IGidToLidChangeListener>> _listeners;

public:
    MockGidToLidChangeHandler() noexcept;
    ~MockGidToLidChangeHandler() override;

    void addListener(std::unique_ptr<IGidToLidChangeListener> listener) override;
    void removeListeners(const std::string &docTypeName, const std::set<std::string> &keepNames) override;

    void notifyPut(IDestructorCallbackSP, document::GlobalId, uint32_t, SerialNum)  override { }
    void notifyRemoves(IDestructorCallbackSP, const std::vector<document::GlobalId> &, SerialNum)  override { }
    std::unique_ptr<IPendingGidToLidChanges> grab_pending_changes() override { return {}; }

    const std::vector<AddEntry> &get_adds() const noexcept { return _adds; }
    const std::vector<RemoveEntry>& get_removes() const noexcept { return _removes; }
    const std::vector<std::unique_ptr<IGidToLidChangeListener>> &getListeners() const { return _listeners; }
};

}
