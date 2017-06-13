// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/cppunittestrunner.h>

#include <cppunit/extensions/TestFactoryRegistry.h>
#include <cppunit/ui/text/TestRunner.h>
#include <cppunit/TextTestProgressListener.h>
#include <vespa/log/log.h>
#include <iostream>

LOG_SETUP(".cppunittestrunner");

using CppUnit::Test;
using CppUnit::TestSuite;

namespace vdstestlib {

namespace {
    struct WantedTestList : public CppUnit::Test::Filter {
        std::vector<std::string> _wanted;
        bool _includeStressTests;

        WantedTestList(int argc, const char *argv[],
                       bool includeStressTests)
            : _wanted(),
              _includeStressTests(includeStressTests)
        {
            for (int i=1; i<argc; ++i) {
                if (argv[i][0] != '-') {
                    std::cerr << "Running tests matching '*"
                              << argv[i] << "*'.\n";
                    _wanted.push_back(argv[i]);
                }
            }
            char* testpat = getenv("TEST_SUBSET");
            if (testpat != 0) {
                std::string pat = std::string("*") + testpat;
                if (pat[pat.size() - 1] != '$') pat += "*";
                std::cerr << "Running tests matching '" << pat << "'."
                        " (Taken from TEST_SUBSET environment variable)\n";
                _wanted.push_back(testpat);
            }
            if (CppUnit::Test::disabledCount > 0) {
                std::cerr << CppUnit::Test::disabledCount
                          << " tests are currently disabled and won't be "
                          << "attempted run.\n";
            }
            if (CppUnit::Test::ignoredCount > 0) {
                std::cerr << CppUnit::Test::ignoredCount
                          << " tests are currently set to ignore failures.\n";
            }
        }

        std::string getWantedString(uint32_t index) const {
            std::string s = _wanted[index];
            if (s[s.size() - 1] == '$') {
                return s.substr(0, s.size() - 1);
            }
            return s;
        }

        bool requiresTailMatch(uint32_t index) const {
            std::string s = _wanted[index];
            return (s[s.size() - 1] == '$');
        }

        bool include(const std::string& name) const override {
            if ((name.find("stress") != std::string::npos ||
                 name.find("Stress") != std::string::npos)
                && !_includeStressTests)
            {
                std::cerr << "Excluding stress test " << name << "\n";
            } else {
                if (_wanted.size() == 0) return true;
                for (uint32_t i=0; i<_wanted.size(); ++i) {
                    std::string::size_type pos = name.rfind(getWantedString(i));
                    if (pos == std::string::npos) continue;
                    if (!requiresTailMatch(i)
                        || pos == name.size() - getWantedString(i).size())
                    {
                        return true;
                    }
                }
            }
            return false;
        }
    };

    struct LogHook : public CppUnit::TextTestProgressListener::TestStartHook {
        std::string lastTest;
        void startedTest(const std::string& testName) override {
            LOG(info, "Starting test: %s", testName.c_str());
            lastTest = testName;
        }
        void stoppedTest() override {
            LOG(info, "Stopped test: %s", lastTest.c_str());
        }
    };
}

void CppUnitTestRunner::listTests(const TestSuite *tests) {
    for (const auto & test : tests->getTests()) {
        std::cout << test->getName() << std::endl;
    }
}

CppUnitTestRunner::CppUnitTestRunner()
{
    std::ios::sync_with_stdio();
}

int
CppUnitTestRunner::run(int argc, const char *argv[])
{
    CppUnit::TextUi::TestRunner runner;
    CppUnit::TestFactoryRegistry& registry(
            CppUnit::TestFactoryRegistry::getRegistry());

    Test *tests = registry.makeTest();
    TestSuite *suite = dynamic_cast<TestSuite *>(tests);

    bool includeStressTests = false;
    bool logStartStop = false;
    bool verbose = false;
    if (getenv("TEST_VERBOSE") != 0) {
        verbose = true;
    }

    for (int i=1; i<argc; ++i) {
        std::string arg(argv[i]);
        if (arg == "--verbose") {
            verbose = true;
            logStartStop = true;
        } else if (arg == "--includestress") {
            includeStressTests = true;
        } else if (arg == "--list") {
            listTests(suite);
            exit(0);
        } else if (argv[i][0] == '-') {
            std::cerr << "Illegal option " << arg << "\n";
            exit(1);
        } else {
            // Arguments will be passed as patterns
        }
    }

    WantedTestList wantedList(argc, argv, includeStressTests);
    suite->filter(wantedList);
    runner.addTest(tests);
    CppUnit::TextTestProgressListener::verboseProgress = verbose;
    if (logStartStop) {
        CppUnit::TextTestProgressListener::startHook.reset(new LogHook);
    }
    return (runner.run("", false) ? 0 : -1);
}

} // vdstestlib
