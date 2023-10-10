// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class vdstestlib::DirConfig
 * \ingroup config
 *
 * \brief Helper class for generating dir config
 *
 * Some components use the same config identifier for config of multiple
 * types. This can not be represented in file or raw config specifications.
 * This helper class make it easy to use dir config, while not generating a
 * lot of config files to check in, but keeping the config that needs to be
 * changed programmatically in the unit test itself.
 *
 * To not make the class complex, all config entries are just key/value pairs.
 * For string config entries make sure you include the double quotes in the
 * value.
 */
#pragma once

#include <list>
#include <map>
#include <string>

namespace vdstestlib {

struct DirConfig {
        // Make some aliases to make it easy to see in header file what is what
    typedef std::string ConfigName;
    typedef std::string ConfigKey;
    typedef std::string ConfigValue;

    struct Config {
        ConfigName defFileName;
        std::list<std::pair<ConfigKey, ConfigValue> > config;
        mutable bool dirtyCache;

        Config(const ConfigName&);
        ~Config();

        void clear() { config.clear(); }
        void set(const ConfigKey&); // Set valueless key, such as array size
        void set(const ConfigKey&, const ConfigValue&);
        template<typename T>
        void setValue(const ConfigKey& key, const T& value);
        void remove(const ConfigKey&);
        const ConfigValue* get(const ConfigKey&) const;
        template<typename T>
        T getValue(const ConfigKey& key, const T& defVal) const;
    };

    DirConfig();
    ~DirConfig();

    // Adjusts the memory representation of this config.
    // publish() to push the config from memory to files.
    Config& addConfig(const ConfigName&); // Complain if existing
    Config& getConfig(const ConfigName&, bool createIfNonExisting = false);
    void removeConfig(const ConfigName&);

    /** Write the configs given to file. */
    void publish() const;

    /** Get the id that should be used to get config from this instance. */
    std::string getConfigId() const;
    std::string getDir() const { return _dirName; }

    /** Return whether memory representation currently differ from files. */
    bool isCacheDirty() const;

private:
    static unsigned int _nextDir;
    std::map<ConfigName, Config> _configs;
    std::string _dirName;
};

} // storage

