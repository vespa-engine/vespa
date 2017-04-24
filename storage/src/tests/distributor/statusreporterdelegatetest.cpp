// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <tests/common/testhelper.h>
#include <tests/distributor/distributortestutil.h>

#include <vespa/storage/distributor/statusreporterdelegate.h>

namespace storage {
namespace distributor {

class StatusReporterDelegateTest : public CppUnit::TestFixture
{
    CPPUNIT_TEST_SUITE(StatusReporterDelegateTest);
    CPPUNIT_TEST(testDelegateInvokesDelegatorOnStatusRequest);
    CPPUNIT_TEST_SUITE_END();

    void testDelegateInvokesDelegatorOnStatusRequest();
};

CPPUNIT_TEST_SUITE_REGISTRATION(StatusReporterDelegateTest);

namespace {

// We really ought to get GoogleMock as part of our testing suite...
class MockDelegator : public StatusDelegator
{
    mutable std::ostringstream _calls;
    bool handleStatusRequest(const DelegatedStatusRequest& request) const override {
        _calls << "Request(" << request.path << ")";
        return request.reporter.reportStatus(request.outputStream, request.path);
    }
public:
    std::string getCalls() const {
        return _calls.str();
    }
};

class MockStatusReporter : public framework::StatusReporter
{
public:
    MockStatusReporter()
        : framework::StatusReporter("foo", "Bar")
    {}
    vespalib::string getReportContentType(
            const framework::HttpUrlPath&) const override
    {
        return "foo/bar";
    }

    bool reportStatus(std::ostream& os,
                      const framework::HttpUrlPath& path) const override
    {
        os << "reportStatus with " << path;
        return true;
    }
};

}

void
StatusReporterDelegateTest::testDelegateInvokesDelegatorOnStatusRequest()
{
    vdstestlib::DirConfig config(getStandardConfig(false));
    TestDistributorApp app(config.getConfigId());

    MockDelegator mockDelegator;
    MockStatusReporter reporter;

    StatusReporterDelegate delegate(app.getComponentRegister(),
                                    mockDelegator,
                                    reporter);
    framework::HttpUrlPath path("dummy");
    CPPUNIT_ASSERT_EQUAL(vespalib::string("foo/bar"),
                         delegate.getReportContentType(path));

    std::ostringstream ss;
    CPPUNIT_ASSERT(delegate.reportStatus(ss, path));

    CPPUNIT_ASSERT_EQUAL(std::string("Request(dummy)"),
                         mockDelegator.getCalls());
    CPPUNIT_ASSERT_EQUAL(std::string("reportStatus with dummy"),
                         ss.str());
}

} // distributor
} // storage
