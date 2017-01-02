// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vdstestlib/cppunit/dirconfig.h>

#include <fstream>
#include <vespa/log/log.h>
#include <sstream>
#include <atomic>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exceptions.h>

LOG_SETUP(".dirconfig");

namespace vdstestlib {

    // When we start up first time, remove old config from the directories
    // we're using
namespace {

class Root {
public:
    Root() :
        _nextDir(0)
    {
        memset(_dirname, 0, sizeof(_dirname));
        sprintf(_dirname, "dirconfig.tmp.XXXXXX");
        char * realName = mkdtemp(_dirname);
        assert(realName == _dirname);
        (void) realName;
    }
    ~Root() {
	if (system((std::string("rm -rf ") + _dirname).c_str()) != 0) {
	    abort();
	}
    }

    std::string nextDir() {
        char name[64];
        uint32_t id = _nextDir++;
        sprintf(name, "%s/%u", _dirname, id);
        return name;
    }
private:
    std::string dir() const { return _dirname; }
    char                  _dirname[64];
    std::atomic<uint32_t> _nextDir;
};

Root _G_root;

}

DirConfig::Config::Config(const ConfigName& name)
    : defFileName(name),
      config(),
      dirtyCache(false)
{
}

void
DirConfig::Config::set(const ConfigKey& key)
{
    set(key, "");
}

void
DirConfig::Config::set(const ConfigKey& key, const ConfigValue& value)
{
    for (std::list<std::pair<ConfigKey, ConfigValue> >::iterator it
            = config.begin(); it != config.end(); ++it)
    {
        if (it->first == key) {
            if (it->second == value) return;
            dirtyCache = true;
            it->second = value;
            return;
        }
    }
    dirtyCache = true;
    config.push_back(std::pair<ConfigKey, ConfigValue>(key, value));
}

void
DirConfig::Config::remove(const ConfigKey& key)
{
    for (std::list<std::pair<ConfigKey, ConfigValue> >::iterator it
            = config.begin(); it != config.end(); ++it)
    {
        if (it->first == key) {
            dirtyCache = true;
            config.erase(it);
            return;
        }
    }
}

const DirConfig::ConfigValue*
DirConfig::Config::get(const ConfigKey& key) const
{
    for (std::list<std::pair<ConfigKey, ConfigValue> >::const_iterator it
            = config.begin(); it != config.end(); ++it)
    {
        if (it->first == key) {
            return &it->second;
        }
    }
    return 0;
}

DirConfig::DirConfig()
    : _configs(),
      _dirName(_G_root.nextDir())
{
    vespalib::mkdir(_dirName, true);
}

DirConfig::Config&
DirConfig::addConfig(const ConfigName& name)
{
    std::pair<std::map<ConfigName, Config>::iterator, bool> result
        = _configs.insert(
            std::map<ConfigName, Config>::value_type(name, Config(name)));
    if (!result.second) {
        throw vespalib::IllegalArgumentException(
            "There is already a config named " + name, VESPA_STRLOC);
    }
    return result.first->second;
}

DirConfig::Config&
DirConfig::getConfig(const ConfigName& name, bool createIfNonExisting)
{
    std::map<ConfigName, Config>::iterator it(_configs.find(name));
    if (it == _configs.end()) {
        if (createIfNonExisting) {
            return addConfig(name);
        }
        throw vespalib::IllegalArgumentException(
            "No config named " + name, VESPA_STRLOC);
    }
    return it->second;
}

template<typename T>
void
DirConfig::Config::setValue(const ConfigKey& key, const T& value)
{
    std::ostringstream ost;
    ost << value;
    set(key, ost.str());
}

template<typename T>
T
DirConfig::Config::getValue(const ConfigKey& key, const T& defVal) const
{
    const ConfigValue* val(get(key));
    if (val == 0) return defVal;
    return boost::lexical_cast<T>(*val);
}

void
DirConfig::removeConfig(const ConfigName& name)
{
    _configs.erase(name);
}

void
DirConfig::publish() const
{
    for (std::map<ConfigName, Config>::const_iterator it = _configs.begin();
         it != _configs.end(); ++it)
    {
        std::string filename = _dirName + "/" + it->first + ".cfg";
        std::ofstream out(filename.c_str());
        for (std::list<std::pair<ConfigKey, ConfigValue> >::const_iterator i
                = it->second.config.begin(); i != it->second.config.end(); ++i)
        {
            if (i->second.size() > 0) {
                out << i->first << " " << i->second << "\n";
            } else {
                out << i->first << "\n";
            }
        }
        out.close();
        LOG(debug, "Wrote config file %s.", filename.c_str());
        it->second.dirtyCache = false;
    }
}

std::string
DirConfig::getConfigId() const
{
        // Users are likely to set up config and then give config ids to users.
        // This is thus a good place to automatically publish changes so users
        // dont need to call publish manually
    if (isCacheDirty()) {
        LOG(debug, "Cache dirty in getConfigId(). Writing config files.");
        publish();
    }
    return "dir:" + _dirName;
}

bool
DirConfig::isCacheDirty() const
{
    for (std::map<ConfigName, Config>::const_iterator it = _configs.begin();
         it != _configs.end(); ++it)
    {
        if (it->second.dirtyCache) return true;
    }
    return false;
}

} // storage
