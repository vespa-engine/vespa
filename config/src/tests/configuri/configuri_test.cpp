// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-my.h"
#include <vespa/config/common/configcontext.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <vespa/config/subscription/configuri.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace config;

namespace {

void assertConfigId(const std::string & expected, const ConfigUri & uri) {
    ASSERT_EQ(expected, uri.getConfigId());
}

}

TEST(ConfigUriTest, Require_that_URI_can_be_created_from_const_char_ptr)
{
    assertConfigId("foo/bar", ConfigUri("foo/bar"));
    assertConfigId("myfile", ConfigUri("file:myfile.cfg"));
    assertConfigId("", ConfigUri("raw:myraw"));
    assertConfigId("", ConfigUri("dir:."));
}

TEST(ConfigUriTest, Require_that_URI_can_be_created_from_std_string)
{
    assertConfigId("foo/bar", ConfigUri(std::string("foo/bar")));
    assertConfigId("myfile", ConfigUri(std::string("file:myfile.cfg")));
    assertConfigId("", ConfigUri(std::string("raw:myraw")));
    assertConfigId("", ConfigUri(std::string("dir:.")));
}

TEST(ConfigUriTest, Require_that_URI_can_be_created_from_instance)
{
    MyConfigBuilder b;
    b.myField = "rabarbra";
    ConfigUri uri(ConfigUri::createFromInstance(b));
    ConfigSubscriber subscriber(uri.getContext());
    ConfigHandle<MyConfig>::UP handle = subscriber.subscribe<MyConfig>(uri.getConfigId());
    ASSERT_TRUE(subscriber.nextConfigNow());
    ASSERT_TRUE(handle->isChanged());
    std::unique_ptr<MyConfig> cfg = handle->getConfig();
    ASSERT_EQ(b.myField, cfg->myField);

}

TEST(ConfigUriTest, Require_that_URI_can_be_forked)
{
    std::shared_ptr<IConfigContext> f1(std::make_shared<ConfigContext>());
    assertConfigId("baz", ConfigUri("foo/bar").createWithNewId("baz"));
    ConfigUri parent("foo", f1);
    ConfigUri child = parent.createWithNewId("baz");
    ASSERT_TRUE(parent.getContext().get() == child.getContext().get());
}

GTEST_MAIN_RUN_ALL_TESTS()
