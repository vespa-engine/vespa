// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/insertion_operators.h>

namespace proton {
namespace test {

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
public:
    MockGidToLidChangeHandler()
        : IGidToLidChangeHandler(),
          _adds(),
          _removes()
    {
    }

    ~MockGidToLidChangeHandler() { }

    virtual void addListener(std::unique_ptr<IGidToLidChangeListener> listener) override {
        _adds.emplace_back(listener->getDocTypeName(), listener->getName());
    }

    virtual void removeListeners(const vespalib::string &docTypeName,
                                 const std::set<vespalib::string> &keepNames) override {
        _removes.emplace_back(docTypeName, keepNames);
    }

    void assertAdds(const std::vector<AddEntry> &expAdds)
    {
        EXPECT_EQUAL(expAdds, _adds);
    }

    void assertRemoves(const std::vector<RemoveEntry> &expRemoves)
    {
        EXPECT_EQUAL(expRemoves, _removes);
    }
};

}
}
