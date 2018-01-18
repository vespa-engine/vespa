// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testbase.h"
#include <vespa/document/base/testdocrepo.h>
#include <vespa/vespalib/util/exception.h>
#include <fcntl.h>
#include <unistd.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".testbase");

using document::DocumentTypeRepo;
using document::readDocumenttypesConfig;

TestBase::TestBase() :
    _repo(new DocumentTypeRepo(readDocumenttypesConfig(
            TEST_PATH("../../../test/cfg/testdoctypes.cfg")))),
    _dataPath(TEST_PATH("../../../test/crosslanguagefiles")),
    _loadTypes(),
    _protocol(_loadTypes, _repo, "", true),
    _tests()
{
    _loadTypes.addLoadType(34, "foo", Priority::PRI_NORMAL_2);
}

int
TestBase::Main()
{
    TEST_INIT("messages_test");

    // Retrieve version number to test for.
    LOG(info, "Running tests for version %s.", getVersion().toString().c_str());

    // Run registered tests.
    for (std::map<uint32_t, TEST_METHOD_PT>::iterator it = _tests.begin();
         it != _tests.end(); ++it)
    {
        LOG(info, "Running test for routable type %d.", it->first);
        EXPECT_TRUE( (this->*(it->second))() );
        TEST_FLUSH();
    }

    // Test routable type coverage.
    std::vector<uint32_t> expected, actual;
    EXPECT_TRUE(testCoverage(expected, actual));
    expected.push_back(0);
    EXPECT_TRUE(!testCoverage(expected, actual));
    actual.push_back(1);
    EXPECT_TRUE(!testCoverage(expected, actual));
    actual.push_back(0);
    EXPECT_TRUE(!testCoverage(expected, actual));
    expected.push_back(1);
    EXPECT_TRUE(testCoverage(expected, actual));

    expected.clear();
    _protocol.getRoutableTypes(getVersion(), expected);

    actual.clear();
    for (std::map<uint32_t, TEST_METHOD_PT>::iterator it = _tests.begin();
         it != _tests.end(); ++it)
    {
        actual.push_back(it->first);
    }
    if (shouldTestCoverage()) {
        EXPECT_TRUE(testCoverage(expected, actual, true));
    }
    TEST_DONE();
}

TestBase &
TestBase::putTest(uint32_t type, TEST_METHOD_PT test)
{
    _tests[type] = test;
    return *this;
}

bool
TestBase::testCoverage(const std::vector<uint32_t> &expected, const std::vector<uint32_t> &actual, bool report) const
{
    bool ret = true;

    std::vector<uint32_t> lst(actual);
    for (std::vector<uint32_t>::const_iterator it = expected.begin();
         it != expected.end(); ++it)
    {
        std::vector<uint32_t>::iterator occ = std::find(lst.begin(), lst.end(), *it);
        if (occ == lst.end()) {
            if (report) {
                LOG(error, "Routable type %d is registered in DocumentProtocol but not tested.", *it);
            }
            ret = false;
        } else {
            lst.erase(occ);
        }
    }
    if (!lst.empty()) {
        if (report) {
            for (std::vector<uint32_t>::iterator it = lst.begin();
                 it != lst.end(); ++it)
            {
                LOG(error, "Routable type %d is tested but not registered in DocumentProtocol.", *it);
            }
        }
        ret = false;
    }

    return ret;
}

uint32_t
TestBase::serialize(const string &filename, const mbus::Routable &routable)
{
    const vespalib::Version version = getVersion();
    string path = getPath(version.toString() + "-cpp-" + filename + ".dat");
    LOG(info, "Serializing to '%s'..", path.c_str());

    mbus::Blob blob = _protocol.encode(version, routable);
    if (!EXPECT_TRUE(writeFile(path, blob))) {
        LOG(error, "Could not open file '%s' for writing.", path.c_str());
        return 0;
    }
    mbus::Routable::UP obj = _protocol.decode(version, blob);
    if (!EXPECT_TRUE(obj.get() != NULL)) {
        LOG(error, "Protocol failed to decode serialized data.");
        return 0;
    }
    if (!EXPECT_TRUE(routable.getType() == obj->getType())) {
        LOG(error, "Expected class %d, got %d.", routable.getType(), obj->getType());
        return 0;
    }
    return blob.size();
}

mbus::Routable::UP
TestBase::deserialize(const string &filename, uint32_t classId, uint32_t lang)
{
    const vespalib::Version version = getVersion();
    string path = getPath(version.toString() + (lang == LANG_JAVA ? "-java" : "-cpp") + "-" + filename + ".dat");
    LOG(info, "Deserializing from '%s'..", path.c_str());

    mbus::Blob blob = readFile(path);
    if (!EXPECT_TRUE(blob.size() != 0)) {
        LOG(error, "Could not open file '%s' for reading.", path.c_str());
        return mbus::Routable::UP();
    }
    mbus::Routable::UP ret = _protocol.decode(version, blob);

    if (!EXPECT_TRUE(ret.get())) {
        LOG(error, "Unable to decode class %d", classId);
    } else if (!EXPECT_TRUE(classId == ret->getType())) {
        LOG(error, "Expected class %d, got %d.", classId, ret->getType());
        return mbus::Routable::UP();
    }
    return ret;
}

void
TestBase::dump(const mbus::Blob& blob) const
{
    fprintf(stderr, "[%ld]: ", blob.size());
    for(size_t i = 0; i < blob.size(); i++) {
        if (blob.data()[i] > 32 && blob.data()[i] < 126) {
            fprintf(stderr, "%c ", blob.data()[i]);
        }
        else {
            fprintf(stderr, "%d ", blob.data()[i]);
        }
    }
    fprintf(stderr, "\n");
}


bool
TestBase::writeFile(const string &filename, const mbus::Blob& blob) const
{
    int file = open(filename.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (file == -1) {
        return false;
    }
    if (write(file, blob.data(), blob.size()) != (ssize_t)blob.size()) {
	throw vespalib::Exception("write failed");
    }
    close(file);
    return true;
}

mbus::Blob
TestBase::readFile(const string &filename) const
{
    int file = open(filename.c_str(), O_RDONLY);
    int len = (file == -1) ? 0 : lseek(file, 0, SEEK_END);
    mbus::Blob blob(len);
    if (file != -1) {
        lseek(file, 0, SEEK_SET);
        if (read(file, blob.data(), len) != len) {
	    throw vespalib::Exception("read failed");
	}
        close(file);
    }

    return blob;
}


