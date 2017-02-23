// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_listener.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_registrator.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <map>
#include <vespa/log/log.h>
LOG_SETUP("gid_to_lid_change_registrator_test");

namespace proton {

class MyListener : public IGidToLidChangeListener
{
    vespalib::string _docTypeName;
    vespalib::string _name;
public:
    MyListener(const vespalib::string &docTypeName, const vespalib::string &name)
        : _docTypeName(docTypeName),
          _name(name)
    {
    }
    virtual ~MyListener() { }
    virtual void notifyGidToLidChange(document::GlobalId, uint32_t) override { }
    virtual void notifyRegistered() override { }
    virtual const vespalib::string &getName() const override { return _name; }
    virtual const vespalib::string &getDocTypeName() const override { return _docTypeName; }
};

using AddEntry = std::pair<vespalib::string, vespalib::string>;
using RemoveEntry = std::pair<vespalib::string, std::set<vespalib::string>>;

class MyHandler : public IGidToLidChangeHandler {
    std::vector<AddEntry> _adds;
    std::vector<RemoveEntry> _removes;
public:
    MyHandler()
        : IGidToLidChangeHandler(),
          _adds(),
          _removes()
    {
    }

    ~MyHandler() { }

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

struct Fixture
{
    std::shared_ptr<MyHandler> _handler;

    Fixture()
        : _handler(std::make_shared<MyHandler>())
    {
    }

    ~Fixture()
    {
    }

    std::unique_ptr<GidToLidChangeRegistrator>
    getRegistrator(const vespalib::string &docTypeName) {
        return std::make_unique<GidToLidChangeRegistrator>(_handler, docTypeName);
    }

    void assertAdds(const std::vector<AddEntry> &expAdds) {
        _handler->assertAdds(expAdds);
    }

    void assertRemoves(const std::vector<RemoveEntry> &expRemoves) {
        _handler->assertRemoves(expRemoves);
    }
};

TEST_F("Test that we can register a listener", Fixture)
{
    auto registrator = f.getRegistrator("testdoc");
    TEST_DO(f.assertAdds({}));
    TEST_DO(f.assertRemoves({}));
    registrator->addListener(std::make_unique<MyListener>("testdoc", "f1"));
    TEST_DO(f.assertAdds({{"testdoc","f1"}}));
    TEST_DO(f.assertRemoves({}));
    registrator.reset();
    TEST_DO(f.assertAdds({{"testdoc","f1"}}));
    TEST_DO(f.assertRemoves({{"testdoc",{"f1"}}}));
}

TEST_F("Test that we can register multiple listeners", Fixture)
{
    auto registrator = f.getRegistrator("testdoc");
    TEST_DO(f.assertAdds({}));
    TEST_DO(f.assertRemoves({}));
    registrator->addListener(std::make_unique<MyListener>("testdoc", "f1"));
    registrator->addListener(std::make_unique<MyListener>("testdoc", "f2"));
    TEST_DO(f.assertAdds({{"testdoc","f1"},{"testdoc","f2"}}));
    TEST_DO(f.assertRemoves({}));
    registrator.reset();
    TEST_DO(f.assertAdds({{"testdoc","f1"},{"testdoc","f2"}}));
    TEST_DO(f.assertRemoves({{"testdoc",{"f1","f2"}}}));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
