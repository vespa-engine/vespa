// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <vespa/config/subscription/configuri.h>
#include "config-my.h"

using namespace config;

namespace {

void assertConfigId(const std::string & expected, const ConfigUri & uri) {
    ASSERT_EQUAL(expected, uri.getConfigId());
}

}
TEST("Require that URI can be created from const char *") {
    assertConfigId("foo/bar", ConfigUri("foo/bar"));
    assertConfigId("myfile", ConfigUri("file:myfile.cfg"));
    assertConfigId("", ConfigUri("raw:myraw"));
    assertConfigId("", ConfigUri("dir:."));
}

TEST("Require that URI can be created from std::string") {
    assertConfigId("foo/bar", ConfigUri(std::string("foo/bar")));
    assertConfigId("myfile", ConfigUri(std::string("file:myfile.cfg")));
    assertConfigId("", ConfigUri(std::string("raw:myraw")));
    assertConfigId("", ConfigUri(std::string("dir:.")));
}

TEST("Require that URI can be created from vespalib::string") {
    assertConfigId("foo/bar", ConfigUri(vespalib::string("foo/bar")));
    assertConfigId("myfile", ConfigUri(vespalib::string("file:myfile.cfg")));
    assertConfigId("", ConfigUri(vespalib::string("raw:myraw")));
    assertConfigId("", ConfigUri(vespalib::string("dir:.")));
}

TEST("Require that URI can be created from  instance") {
    MyConfigBuilder b;
    b.myField = "rabarbra";
    ConfigUri uri(ConfigUri::createFromInstance(b));
    ConfigSubscriber subscriber(uri.getContext());
    ConfigHandle<MyConfig>::UP handle = subscriber.subscribe<MyConfig>(uri.getConfigId());
    ASSERT_TRUE(subscriber.nextConfigNow());
    ASSERT_TRUE(handle->isChanged());
    std::unique_ptr<MyConfig> cfg = handle->getConfig();
    ASSERT_EQUAL(b.myField, cfg->myField);

}

TEST_F("Require that URI can be \"forked\"", std::shared_ptr<IConfigContext>(std::make_shared<ConfigContext>())) {
    assertConfigId("baz", ConfigUri("foo/bar").createWithNewId("baz"));
    ConfigUri parent("foo", f1);
    ConfigUri child = parent.createWithNewId("baz");
    ASSERT_TRUE(parent.getContext().get() == child.getContext().get());
}

TEST_MAIN() { TEST_RUN_ALL(); }
