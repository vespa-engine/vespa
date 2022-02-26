// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config/common/compressiontype.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <memory>

class FNET_Transport;

namespace config {

class ConfigInstance;
class SourceFactory;
struct TimingValues;

typedef vespalib::string SourceSpecKey;

/**
 * A source spec is a user provided specification of which sources to fetch
 * config from.
 */
class SourceSpec
{
public:
    using UP = std::unique_ptr<SourceSpec>; /// Convenience typedef

    /**
     * Creates a source factory from which to create config sources for new
     * subscriptions. The UpdateHandler should be
     * provided to the source for it to post any update given any config
     * request.
     *
     * @param handler A pointer to the update handler that will receive config
     *                updates from the source.
     * @param timingValues Timing values to be used for this source.
     * @return An std::unique_ptr<Source> that can be used to ask for config.
     */
    virtual std::unique_ptr<SourceFactory> createSourceFactory(const TimingValues & timingValues) const = 0;
    virtual ~SourceSpec() = default;
};


/**
 * A RawSpec gives the ability to specify config as a raw config string.
 */
class RawSpec : public SourceSpec
{
public:
    /**
     * Constructs a new RawSpec that can be sent with a subscribe call.
     *
     * @param config The config represented as a raw string.
     */
    RawSpec(const vespalib::string & config);

    std::unique_ptr<SourceFactory> createSourceFactory(const TimingValues & timingValues) const override;

    /**
     * Returns the string representation of this config.
     *
     * @return the config in a string.
     */
    const vespalib::string & toString() const { return _config; }
private:
    vespalib::string _config;
};

/**
 * A FileSpec gives the ability to serve config from a file. The filenames in
 * this spec must match the config definition name when subscribing.
 */
class FileSpec : public SourceSpec
{
public:
    /**
     * Creates a FileSpec to serve config from a file. Multiple files may be
     * added to the spec.
     *
     * @param fileName Path to the file to serve config from.
     */
    FileSpec(const vespalib::string & fileName);

    /**
     * Get the file name of this spec.
     *
     * @return the filename from which to serve config.
     */
    const vespalib::string & getFileName() const { return _fileName; }

    std::unique_ptr<SourceFactory> createSourceFactory(const TimingValues & timingValues) const override;
private:
    void verifyName(const vespalib::string & fileName);
    vespalib::string _fileName;
};

/**
 * A DirSpec gives the ability to serve config from a directory.
 */
class DirSpec : public SourceSpec
{
public:
    /**
     * Create a DirSpec to serve config from. The files within this directory
     * must have the names of the config definition ending with a .cfg suffix.
     *
     * @param dirName Directory to serve config from.
     */
    DirSpec(const vespalib::string & dirName);
    ~DirSpec() override;

    /**
     * Get directory handled by this spec.
     *
     * @return the directory from which to serve config.
     */
    const vespalib::string & getDirName() const { return _dirName; }

    std::unique_ptr<SourceFactory> createSourceFactory(const TimingValues & timingValues) const override;
private:
    vespalib::string _dirName;
};

/**
 * A server spec is a user provided specification of one or more config servers
 * that may provide config.
 */
class ServerSpec : public SourceSpec
{
public:
    /// A list of host specifications
    using HostSpecList = std::vector<vespalib::string>;

    /**
     * Construct a ServerSpec that fetches the host specs from the
     * VESPA_CONFIG_SOURCES environment variable.
     */
    ServerSpec();

    /**
     * Construct a ServerSpec with a list host specifications on the form
     * tcp/hostname:port
     *
     * @param list a list of host specifications.
     */
    ServerSpec(const HostSpecList & list);

    /**
     * Construct a ServerSpec with a host specification.
     *
     * @param hostSpec the host specification on the form "tcp/hostname:port"
     */
    ServerSpec(const vespalib::string & hostSpec);

    std::unique_ptr<SourceFactory> createSourceFactory(const TimingValues & timingValues) const override;

    /**
     * Inspect how many hosts this source refers to.
     *
     * @return the number of hosts referred.
     */
    size_t numHosts() const { return _hostList.size(); }

    /**
     * Retrieve host specification element i
     *
     * @param i the spec element to retrieve.
     */
    const vespalib::string & getHost(size_t i) const { return _hostList[i]; }

    /**
     * Get the protocol version as parsed by this source spec.
     */
    int protocolVersion() const { return _protocolVersion; }

    /**
     * Get the trace level as parsed by this source spec.
     */
    int traceLevel() const { return _traceLevel; }

    /**
     * Get the compression type as parsed by this source spec.
     */
    CompressionType compressionType() const { return _compressionType; }
private:
    void initialize(const vespalib::string & hostSpec);
    HostSpecList          _hostList;
    const int             _protocolVersion;
    const int             _traceLevel;
    const CompressionType _compressionType;
    const static int DEFAULT_PROXY_PORT = 19090;
};

/**
 * A ServerSpec that allows providing externally supplied transport
 */
class ConfigServerSpec : public config::ServerSpec {
public:
    ConfigServerSpec(FNET_Transport & transport);
    ~ConfigServerSpec() override;
    std::unique_ptr<SourceFactory> createSourceFactory(const TimingValues & timingValues) const override;
private:
    FNET_Transport & _transport;
};



/**
 * A ConfigSet gives the ability to serve config from a set of ConfigInstance
 * builders.
 */

class BuilderMap;

class ConfigSet : public SourceSpec
{
public:
    /// Constructs a new empty ConfigSet
    ConfigSet();

    using BuilderMapSP = std::shared_ptr<BuilderMap>;
    /**
     * Add a builder to serve config from. The builder must be of a
     * 'ConfigType'Builder, and the configId must be the id to which you want to
     * serve the config generated by this builder.
     *
     * @param configId The configId that should be used to get the config
     *                 produced by this builder.
     * @param builder A builder instance that you can use to change config later
     *                and then call reload on the ConfigContext object.
     */
    void addBuilder(const vespalib::string & configId, ConfigInstance * builder);

    std::unique_ptr<SourceFactory> createSourceFactory(const TimingValues & timingValues) const override;
private:
    BuilderMapSP _builderMap;
};

}
