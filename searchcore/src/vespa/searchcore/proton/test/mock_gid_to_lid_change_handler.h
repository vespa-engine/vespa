// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_listener.h>
#include <vespa/searchcore/proton/reference/i_pending_gid_to_lid_changes.h>
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/test/insertion_operators.h>

namespace proton::test {

/*
 * Mockup of gid to lid change handler, used by unit tests to track
 * proper add/remove of listeners.
 */
class MockGidToLidChangeHandler : public IGidToLidChangeHandler {
public:
    using AddEntry = std::pair<vespalib::string, vespalib::string>;
    using RemoveEntry = std::pair<vespalib::string, std::set<vespalib::string>>;

private:
    std::vector<AddEntry> _adds;
    std::vector<RemoveEntry> _removes;
    std::vector<std::unique_ptr<IGidToLidChangeListener>> _listeners;

public:
    MockGidToLidChangeHandler() noexcept;
    ~MockGidToLidChangeHandler() override;

    void addListener(std::unique_ptr<IGidToLidChangeListener> listener) override;
    void removeListeners(const vespalib::string &docTypeName, const std::set<vespalib::string> &keepNames) override;

    void notifyPut(IDestructorCallbackSP, document::GlobalId, uint32_t, SerialNum)  override { }
    void notifyRemoves(IDestructorCallbackSP, const std::vector<document::GlobalId> &, SerialNum)  override { }
    std::unique_ptr<IPendingGidToLidChanges> grab_pending_changes() override { return {}; }

    void assertAdds(const std::vector<AddEntry> &expAdds) const;
    void assertRemoves(const std::vector<RemoveEntry> &expRemoves) const;

    const std::vector<std::unique_ptr<IGidToLidChangeListener>> &getListeners() const { return _listeners; }
};

}
