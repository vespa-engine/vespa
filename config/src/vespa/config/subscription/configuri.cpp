// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "configuri.h"
#include "configinstancespec.h"
#include <vespa/config/common/configcontext.h>
#include <vespa/config/helper/legacy.h>

namespace {
bool checkEmpty(const vespalib::string & configId) {
    return configId.empty();
}
}
namespace config {

ConfigUri::ConfigUri(vespalib::stringref configId)
    : _configId(legacyConfigId2ConfigId(configId)),
      _context(std::make_shared<ConfigContext>(*legacyConfigId2Spec(configId))),
      _empty(checkEmpty(configId))
{
}

ConfigUri::ConfigUri(const vespalib::string &configId, std::shared_ptr<IConfigContext> context)
    : _configId(configId),
      _context(std::move(context)),
      _empty(false)
{
}

ConfigUri::~ConfigUri() = default;

ConfigUri
ConfigUri::createWithNewId(const vespalib::string & configId) const
{
    return ConfigUri(configId, _context);
}

const vespalib::string & ConfigUri::getConfigId() const { return _configId; }
const std::shared_ptr<IConfigContext> & ConfigUri::getContext() const { return _context; }

ConfigUri
ConfigUri::createFromInstance(const ConfigInstance & instance)
{
    return ConfigUri("", std::make_shared<ConfigContext>(ConfigInstanceSpec(instance)));
}

ConfigUri
ConfigUri::createEmpty()
{
    ConfigUri uri("", std::make_shared<ConfigContext>(RawSpec("")));
    uri._empty = true;
    return uri;
}

ConfigUri ConfigUri::createFromSpec(const vespalib::string& configId, const SourceSpec& spec)
{
    return ConfigUri(configId, std::make_shared<ConfigContext>(spec));
}


} // namespace config
