// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for feeddebugger.

#include <vespa/document/base/documentid.h>
#include <vespa/searchcore/proton/common/feeddebugger.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::DocumentId;
using std::string;
using namespace proton;

namespace {

const char lid_env_name[] = "VESPA_PROTON_DEBUG_FEED_LID_LIST";
const char docid_env_name[] = "VESPA_PROTON_DEBUG_FEED_DOCID_LIST";

class EnvSaver {
    const char *_name;
    string _value;
    bool _is_set;

public:
    EnvSaver(const char *name) : _name(name) {
        char *val = getenv(_name);
        _is_set = val;
        if (val) {
            _value = val;
        }
    }
    ~EnvSaver() {
        if (_is_set) {
            setenv(_name, _value.c_str(), true);
        } else {
            unsetenv(_name);
        }
    }
};

}

TEST(FeedDebuggerTest, require_that_when_environment_variable_is_not_set_debugging_is_off)
{
    EnvSaver save_lid_env(lid_env_name);
    EnvSaver save_docid_env(docid_env_name);
    FeedDebugger debugger;
    EXPECT_FALSE(debugger.isDebugging());
}

TEST(FeedDebuggerTest, require_that_setting_an_environment_variable_turns_on_lid_specific_debugging)
{
    EnvSaver save_lid_env(lid_env_name);
    EnvSaver save_docid_env(docid_env_name);
    setenv(lid_env_name, "1,3,5", true);

    FeedDebugger debugger;
    EXPECT_TRUE(debugger.isDebugging());
    EXPECT_EQ(ns_log::Logger::info, debugger.getDebugLevel(1, 0));
    EXPECT_EQ(ns_log::Logger::spam, debugger.getDebugLevel(2, 0));
    EXPECT_EQ(ns_log::Logger::info, debugger.getDebugLevel(3, 0));
    EXPECT_EQ(ns_log::Logger::spam, debugger.getDebugLevel(4, 0));
    EXPECT_EQ(ns_log::Logger::info, debugger.getDebugLevel(5, 0));
}

TEST(FeedDebuggerTest, require_that_setting_an_environment_variable_turns_on_docid_specific_debugging)
{
    EnvSaver save_lid_env(lid_env_name);
    EnvSaver save_docid_env(docid_env_name);
    setenv(docid_env_name, "id:ns:type::test:foo,id:ns:type::test:bar,id:ns:type::test:baz", true);

    FeedDebugger debugger;
    EXPECT_TRUE(debugger.isDebugging());
    EXPECT_EQ(ns_log::Logger::info, debugger.getDebugLevel(1, DocumentId("id:ns:type::test:foo")));
    EXPECT_EQ(ns_log::Logger::info, debugger.getDebugLevel(1, DocumentId("id:ns:type::test:bar")));
    EXPECT_EQ(ns_log::Logger::info, debugger.getDebugLevel(1, DocumentId("id:ns:type::test:baz")));
    EXPECT_EQ(ns_log::Logger::spam, debugger.getDebugLevel(1, DocumentId("id:ns:type::test:qux")));
}
