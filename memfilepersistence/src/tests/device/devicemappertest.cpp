// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/memfilepersistence/device/devicemapper.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/exceptions.h>

namespace storage {

namespace memfile {

class DeviceMapperTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(DeviceMapperTest);
    CPPUNIT_TEST(testSimpleDeviceMapper);
    CPPUNIT_TEST(testAdvancedDeviceMapper);
    CPPUNIT_TEST_SUITE_END();

public:
    void testSimpleDeviceMapper();
    void testAdvancedDeviceMapper();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DeviceMapperTest);

void DeviceMapperTest::testSimpleDeviceMapper()
{
    SimpleDeviceMapper mapper;
    CPPUNIT_ASSERT_EQUAL(uint64_t(1), mapper.getDeviceId("whatever&�"));
    CPPUNIT_ASSERT_EQUAL(uint64_t(1), mapper.getDeviceId("whatever&�"));
    CPPUNIT_ASSERT_EQUAL(uint64_t(2), mapper.getDeviceId("whatnot"));
    std::string expected("Whatever& �=)/%#)=");
    CPPUNIT_ASSERT_EQUAL(expected, mapper.getMountPoint(expected));
}

void DeviceMapperTest::testAdvancedDeviceMapper()
{
    AdvancedDeviceMapper mapper;
    try{
        mapper.getDeviceId("/doesnotexist");
        CPPUNIT_FAIL("Expected exception");
    } catch (vespalib::Exception& e) {
        std::string what(e.what());
        CPPUNIT_ASSERT_CONTAIN(
                "Failed to run stat to find data on file /doesnotexist", what);
    }
}

}

} // storage
