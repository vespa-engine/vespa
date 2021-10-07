// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace config {

template <typename ConfigType>
std::unique_ptr<ConfigType>
ConfigValue::newInstance() const
{
    if (_payload) {
        const vespalib::slime::Inspector & payload(_payload->getSlimePayload());
        return std::unique_ptr<ConfigType>(new ConfigType(config::ConfigPayload(payload)));
    } else {
        return std::unique_ptr<ConfigType>(new ConfigType(*this));
    }
}

}
