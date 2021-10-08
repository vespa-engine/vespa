// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configinstancesourcefactory.h"

namespace {

class ConfigInstanceSource : public config::Source {
public:
    ConfigInstanceSource(const config::IConfigHolder::SP & holder, const vespalib::asciistream & buffer)
        : _holder(holder),
          _buffer(buffer),
          _generation(-1)
    { }
    void close() override { }
    void getConfig() override {
        std::vector<vespalib::string> lines(_buffer.getlines());
        std::string currentXxhash64(config::calculateContentXxhash64(lines));
        _holder->handle(config::ConfigUpdate::UP(new config::ConfigUpdate(config::ConfigValue(lines, currentXxhash64), true, _generation)));

    }
    void reload(int64_t generation) override { _generation = generation; }
private:
    config::IConfigHolder::SP _holder;
    vespalib::asciistream _buffer;
    int64_t _generation;
};

}

namespace config {

ConfigInstanceSourceFactory::ConfigInstanceSourceFactory(const ConfigKey & key, const vespalib::asciistream & buffer)
    : _key(key),
      _buffer(buffer)
{
}

Source::UP
ConfigInstanceSourceFactory::createSource(const IConfigHolder::SP & holder, const ConfigKey & key) const
{
    (void) key;
    // TODO: Check key against _key
    return Source::UP(new ConfigInstanceSource(holder, _buffer));
}

} // namespace config

