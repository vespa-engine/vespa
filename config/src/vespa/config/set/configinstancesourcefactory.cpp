// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configinstancesourcefactory.h"
#include <vespa/config/common/iconfigholder.h>

namespace {

class ConfigInstanceSource : public config::Source {
public:
    ConfigInstanceSource(std::shared_ptr<config::IConfigHolder> holder, vespalib::asciistream buffer)
        : _holder(std::move(holder)),
          _buffer(std::move(buffer)),
          _generation(-1)
    { }
    void close() override { }
    void getConfig() override {
        _holder->handle(std::make_unique<config::ConfigUpdate>(config::ConfigValue(config::getlines(_buffer)), true, _generation));

    }
    void reload(int64_t generation) override { _generation = generation; }
private:
    std::shared_ptr<config::IConfigHolder> _holder;
    vespalib::asciistream _buffer;
    int64_t _generation;
};

}

namespace config {

ConfigInstanceSourceFactory::ConfigInstanceSourceFactory(const ConfigKey & key, vespalib::asciistream buffer)
    : _key(key),
      _buffer(std::move(buffer))
{
}

std::unique_ptr<Source>
ConfigInstanceSourceFactory::createSource(std::shared_ptr<IConfigHolder> holder, const ConfigKey & key) const
{
    (void) key;
    // TODO: Check key against _key
    return std::make_unique<ConfigInstanceSource>(std::move(holder), _buffer);
}

} // namespace config

