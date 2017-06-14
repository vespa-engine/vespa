// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

ConfigUri::ConfigUri(const vespalib::string &configId)
    : _configId(legacyConfigId2ConfigId(configId)),
      _context(new ConfigContext(*legacyConfigId2Spec(configId))),
      _empty(checkEmpty(configId))
{
}

ConfigUri::ConfigUri(const vespalib::string &configId, const IConfigContext::SP & context)
    : _configId(configId),
      _context(context),
      _empty(false)
{
}

ConfigUri::~ConfigUri() { }

ConfigUri
ConfigUri::createWithNewId(const vespalib::string & configId) const
{
    return ConfigUri(configId, _context);
}

const vespalib::string & ConfigUri::getConfigId() const { return _configId; }
const IConfigContext::SP & ConfigUri::getContext() const { return _context; }

ConfigUri
ConfigUri::createFromInstance(const ConfigInstance & instance)
{
    return ConfigUri("", IConfigContext::SP(new ConfigContext(ConfigInstanceSpec(instance))));
}

ConfigUri
ConfigUri::createEmpty()
{
    ConfigUri uri("", IConfigContext::SP(new ConfigContext(RawSpec(""))));
    uri._empty = true;
    return uri;
}

ConfigUri ConfigUri::createFromSpec(const vespalib::string& configId, const SourceSpec& spec)
{
    return ConfigUri(configId, IConfigContext::SP(new ConfigContext(spec)));
}


} // namespace config
