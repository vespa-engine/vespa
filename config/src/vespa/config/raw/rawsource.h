// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/source.h>
#include <vespa/config/common/types.h>
namespace config {

class IConfigHolder;

/**
 * Class for sending and receiving config request from a raw string.
 */
class RawSource : public Source {
public:
    RawSource(std::shared_ptr<IConfigHolder> holder, const vespalib::string & payload);
    RawSource(const RawSource &) = delete;
    RawSource & operator = (const RawSource &) = delete;
    ~RawSource() override;
    void getConfig() override;
    void reload(int64_t generation) override;
    void close() override;
private:
    std::shared_ptr<IConfigHolder> _holder;
    StringVector readConfig();
    const vespalib::string _payload;
};

}

