// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <boost/noncopyable.hpp>
#include <boost/filesystem/path.hpp>
#include <boost/signals2.hpp>
#include <boost/enable_shared_from_this.hpp>

#include <vespa/filedistribution/common/buffer.h>
#include <vespa/filedistribution/common/exception.h>
#include <vespa/filedistribution/common/exceptionrethrower.h>

struct _zhandle;
typedef _zhandle zhandle_t;

namespace filedistribution {

namespace errorinfo {
typedef boost::error_info<struct tag_Path, boost::filesystem::path> Path;
}

class ZKException : public Exception {
protected:
    ZKException() {}
};

struct ZKNodeDoesNotExistsException : public ZKException {
    const char* what() const throw() {
        return "Zookeeper: The node does not exist(ZNONODE).";
    }
};

struct ZKNodeExistsException : public ZKException {
    const char* what() const throw() {
        return "Zookeeper: The node already exists(ZNODEEXISTS).";
    }
};

struct ZKGenericException : public ZKException {
    const int _zkStatus;
    ZKGenericException(int zkStatus)
        :_zkStatus(zkStatus)
    {}

    const char* what() const throw();
};

struct ZKFailedConnecting : public ZKException {
    const char* what() const throw() {
        return "Zookeeper: Failed connecting to the zookeeper servers.";
    }
};

class ZKSessionExpired : public ZKException {};

const std::string
diagnosticUserLevelMessage(const ZKException& zk);



class ZKFacade : boost::noncopyable, public boost::enable_shared_from_this<ZKFacade> {
    volatile bool _retriesEnabled;
    volatile bool _watchersEnabled;

    boost::shared_ptr<ExceptionRethrower> _exceptionRethrower;
    zhandle_t* _zhandle;
    const static int _zkSessionTimeOut = 30 * 1000;
    const static size_t _maxDataSize = 1024 * 1024;

    class ZKWatcher;
    static void stateWatchingFun(zhandle_t*, int type, int state, const char* path, void* context);
public:
    typedef boost::shared_ptr<ZKFacade> SP;

    /* Lifetime is managed by ZKFacade.
       Derived classes should only contain weak_ptrs to other objects
       to avoid linking their lifetime to the ZKFacade lifetime.
     */
    class NodeChangedWatcher : boost::noncopyable {
      public:
        virtual void operator()() = 0;
        virtual ~NodeChangedWatcher() {};
    };

    typedef boost::shared_ptr<NodeChangedWatcher> NodeChangedWatcherSP;
    typedef boost::filesystem::path Path;

    ZKFacade(const std::string& zkservers, const boost::shared_ptr<ExceptionRethrower> &);
    ~ZKFacade();

    bool hasNode(const Path&);
    bool hasNode(const Path&, const NodeChangedWatcherSP&);

    const std::string getString(const Path&);
    const Move<Buffer> getData(const Path&);  //throws ZKNodeDoesNotExistsException
    //if watcher is specified, it will be set even if the node does not exists
    const Move<Buffer> getData(const Path&, const NodeChangedWatcherSP&);  //throws ZKNodeDoesNotExistsException

    //Parent path must exist
    void setData(const Path&, const Buffer& buffer, bool mustExist = false);
    void setData(const Path&, const char* buffer, size_t length, bool mustExist = false);

    const Path createSequenceNode(const Path&, const char* buffer, size_t length);

    void remove(const Path&); //throws ZKNodeDoesNotExistsException
    void removeIfExists(const Path&);

    void retainOnly(const Path&, const std::vector<std::string>& children);

    void addEphemeralNode(const Path& path);
    std::vector<std::string> getChildren(const Path& path);
    std::vector<std::string> getChildren(const Path& path, const NodeChangedWatcherSP&);  //throws ZKNodeDoesNotExistsException

    //only for use by shutdown code.
    void disableRetries();
    bool retriesEnabled() {
        return _retriesEnabled;
    }

private:
    void* registerWatcher(const NodeChangedWatcherSP &); //returns watcherContext
    boost::shared_ptr<ZKWatcher> unregisterWatcher(void* watcherContext);
    void invokeWatcher(void* watcherContext);

    boost::mutex _watchersMutex;
    typedef std::map<void*, boost::shared_ptr<ZKWatcher> > WatchersMap;
    WatchersMap _watchers;
};

class ZKLogging {
public:
    ZKLogging();
    ~ZKLogging();
    ZKLogging(const ZKLogging &) = delete;
    ZKLogging & operator = (const ZKLogging &) = delete;
private:
    FILE * _file;
};

} //namespace filedistribution

