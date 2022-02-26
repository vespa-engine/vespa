// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "sourcespec.h"
#include "configinstancespec.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/raw/rawsourcefactory.h>
#include <vespa/config/file/filesourcefactory.h>
#include <vespa/config/frt/frtsourcefactory.h>
#include <vespa/config/frt/frtconnectionpool.h>
#include <vespa/config/frt/protocol.h>
#include <vespa/config/set/configsetsourcefactory.h>
#include <vespa/config/set/configinstancesourcefactory.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/config/print/asciiconfigwriter.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fnet/transport.h>
#include <vespa/fastos/thread.h>
#include <cassert>

namespace config {

class BuilderMap : public std::map<ConfigKey, ConfigInstance *> {
    using Parent = std::map<ConfigKey, ConfigInstance *>;
    using Parent::Parent;
};

RawSpec::RawSpec(const vespalib::string & config)
    : _config(config)
{
}

std::unique_ptr<SourceFactory>
RawSpec::createSourceFactory(const TimingValues &) const
{
    return std::make_unique<RawSourceFactory>(_config);
}

FileSpec::FileSpec(const vespalib::string & fileName)
    : _fileName(fileName)
{
    verifyName(_fileName);
}

void
FileSpec::verifyName(const vespalib::string & fileName)
{
    if (fileName.length() > 4) {
        std::string ending(fileName.substr(fileName.length() - 4, 4));
        if (ending.compare(".cfg") != 0)
            throw InvalidConfigSourceException("File name '" + fileName + "' is invalid, must end with .cfg");
    } else {
        throw InvalidConfigSourceException("File name '" + fileName + "' is invalid");
    }
}

std::unique_ptr<SourceFactory>
FileSpec::createSourceFactory(const TimingValues & ) const
{
    return std::make_unique<FileSourceFactory>(*this);
}

DirSpec::DirSpec(const vespalib::string & dirName)
    : _dirName(dirName)
{
}

DirSpec::~DirSpec() = default;

std::unique_ptr<SourceFactory>
DirSpec::createSourceFactory(const TimingValues & ) const
{
    return std::make_unique<DirSourceFactory>(*this);
}

ServerSpec::ServerSpec()
    : _hostList(),
      _protocolVersion(protocol::readProtocolVersion()),
      _traceLevel(protocol::readTraceLevel()),
      _compressionType(protocol::readProtocolCompressionType())
{
    char* cfgSourcesPtr = getenv("VESPA_CONFIG_SOURCES");
    if (cfgSourcesPtr != nullptr) {
        vespalib::string cfgSourcesStr(cfgSourcesPtr);
        initialize(cfgSourcesStr);
    } else {
        initialize("localhost");
    }
}

void
ServerSpec::initialize(const vespalib::string & hostSpec)
{
    typedef vespalib::StringTokenizer tokenizer;
    tokenizer tok(hostSpec, ",");
    for (tokenizer::Iterator it = tok.begin(); it != tok.end(); it++) {
        vespalib::string srcHost = *it;
        vespalib::asciistream spec;
        if (srcHost.find("tcp/") == std::string::npos) {
            spec << "tcp/";
        }
        spec << srcHost;
        if (srcHost.find(":") == std::string::npos) {
            spec << ":" << DEFAULT_PROXY_PORT;
        }
        _hostList.push_back(spec.str());
    }
}

ServerSpec::ServerSpec(const HostSpecList & hostList)
    : _hostList(hostList),
      _protocolVersion(protocol::readProtocolVersion()),
      _traceLevel(protocol::readTraceLevel()),
      _compressionType(protocol::readProtocolCompressionType())
{
}

ServerSpec::ServerSpec(const vespalib::string & hostSpec)
    : _hostList(),
      _protocolVersion(protocol::readProtocolVersion()),
      _traceLevel(protocol::readTraceLevel()),
      _compressionType(protocol::readProtocolCompressionType())
{
    initialize(hostSpec);
}

std::unique_ptr<SourceFactory>
ServerSpec::createSourceFactory(const TimingValues & timingValues) const
{
    const auto vespaVersion = VespaVersion::getCurrentVersion();
    return std::make_unique<FRTSourceFactory>(
            std::make_unique<FRTConnectionPoolWithTransport>(std::make_unique<FastOS_ThreadPool>(64_Ki),
                                                             std::make_unique<FNET_Transport>(),
                                                             *this, timingValues),
            timingValues, _traceLevel, vespaVersion, _compressionType);
}

ConfigServerSpec::ConfigServerSpec(FNET_Transport & transport)
    : ServerSpec(),
      _transport(transport)
{
}

ConfigServerSpec::~ConfigServerSpec() = default;

std::unique_ptr<SourceFactory>
ConfigServerSpec::createSourceFactory(const TimingValues & timingValues) const
{
    const auto vespaVersion = VespaVersion::getCurrentVersion();
    return std::make_unique<FRTSourceFactory>(
            std::make_unique<FRTConnectionPool>(_transport, *this, timingValues),
            timingValues, traceLevel(), vespaVersion, compressionType());
}

ConfigSet::ConfigSet()
    : _builderMap(std::make_unique<BuilderMap>())
{
}

std::unique_ptr<SourceFactory>
ConfigSet::createSourceFactory(const TimingValues & ) const
{
    return std::make_unique<ConfigSetSourceFactory>(_builderMap);
}

void
ConfigSet::addBuilder(const vespalib::string & configId, ConfigInstance * builder)
{
    assert(builder != nullptr);
    BuilderMap & builderMap(*_builderMap);
    const ConfigKey key(configId, builder->defName(), builder->defNamespace(), builder->defMd5());
    builderMap[key] = builder;
}

ConfigInstanceSpec::ConfigInstanceSpec(const ConfigInstance& instance)
    : _key("", instance.defName(), instance.defNamespace(), instance.defMd5()),
      _buffer()
{
    AsciiConfigWriter writer(_buffer);
    writer.write(instance);
}

ConfigInstanceSpec::~ConfigInstanceSpec() = default;

std::unique_ptr<SourceFactory>
ConfigInstanceSpec::createSourceFactory(const TimingValues& ) const
{
    return std::make_unique<ConfigInstanceSourceFactory>(_key, _buffer);
}


}
