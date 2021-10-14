// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/source.h>
#include <vespa/config/common/iconfigholder.h>
#include <vespa/vespalib/stllike/string.h>

namespace config {

/**
 * Class for sending and receiving config request from a raw string.
 */
class RawSource : public Source {
public:
    RawSource(const IConfigHolder::SP & holder, const vespalib::string & payload);

    void getConfig() override;
    void reload(int64_t generation) override;
    void close() override;
private:
    IConfigHolder::SP _holder;
    std::vector<vespalib::string> readConfig();
    const vespalib::string _payload;
};

}

