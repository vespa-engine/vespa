// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>
#include <vespa/messagebus/routable.h>
#include <vespa/vespalib/component/version.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace documentapi;

/**
 * Declare the signature of the test method.
 */
class TestBase;
typedef bool (TestBase::*TEST_METHOD_PT)();
#define TEST_METHOD(pt) ((TEST_METHOD_PT)&pt)

/**
 * This is the test base itself. It offers a set of utility functions that reflect on the version returned by
 * the pure virtual getVersion() function. You need to inherit this and assign a version and a set of message
 * tests to it.
 */
class TestBase : public vespalib::TestApp {
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
protected:
    const string                  _dataPath;
    LoadTypeSet                        _loadTypes;
    DocumentProtocol                   _protocol;
    std::map<uint32_t, TEST_METHOD_PT> _tests;

    // Declares what languages share serialization.
    enum {
        LANG_CPP = 0,
        LANG_JAVA,
        NUM_LANGUAGES
    };

    TestBase();
    virtual ~TestBase() { /* empty */ }
    virtual const vespalib::Version getVersion() const = 0;
    virtual bool shouldTestCoverage() const = 0;
    TestBase &putTest(uint32_t type, TEST_METHOD_PT test);
    int Main() override;

public:
    const document::DocumentTypeRepo &getTypeRepo() { return *_repo; }
    std::shared_ptr<const document::DocumentTypeRepo> &getTypeRepoSp() { return _repo; }

    bool testCoverage(const std::vector<uint32_t> &expected, const std::vector<uint32_t> &actual, bool report = false) const;
    bool writeFile(const string &filename, const mbus::Blob& blob) const;
    mbus::Blob readFile(const string &filename) const;
    uint32_t serialize(const string &filename, const mbus::Routable &routable);
    mbus::Routable::UP deserialize(const string &filename, uint32_t classId, uint32_t lang);
    void dump(const mbus::Blob &blob) const;

    string getPath(const string &filename) const { return _dataPath + "/" + filename; }
    mbus::Blob encode(const mbus::Routable &obj) const { return _protocol.encode(getVersion(), obj); }
    mbus::Routable::UP decode(mbus::BlobRef data) const { return _protocol.decode(getVersion(), data); }
};

