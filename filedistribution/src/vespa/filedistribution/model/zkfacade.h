// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <map>
#include <mutex>

#include <vespa/filedistribution/common/buffer.h>
#include <vespa/filedistribution/common/exception.h>
#include <vespa/vespalib/util/exception.h>

struct _zhandle;
typedef _zhandle zhandle_t;

namespace filedistribution {

class ZKException : public vespalib::Exception {
protected:
    using vespalib::Exception::Exception;
};

VESPA_DEFINE_EXCEPTION(ZKNodeDoesNotExistsException, ZKException);
VESPA_DEFINE_EXCEPTION(ZKConnectionLossException, ZKException);
VESPA_DEFINE_EXCEPTION(ZKNodeExistsException, ZKException);
VESPA_DEFINE_EXCEPTION(ZKFailedConnecting, ZKException);
VESPA_DEFINE_EXCEPTION(ZKOperationTimeoutException, ZKException);
VESPA_DEFINE_EXCEPTION(ZKSessionExpired, ZKException);

class ZKGenericException : public ZKException {
public:
    ZKGenericException(int zkStatus, const vespalib::stringref &msg, const vespalib::stringref &location = "", int skipStack = 0) :
        ZKException(msg, location, skipStack),
        _zkStatus(zkStatus)
    { }
    ZKGenericException(int zkStatus, const vespalib::Exception &cause, const vespalib::stringref &msg = "",
                        const vespalib::stringref &location = "", int skipStack = 0) :
        ZKException(msg, cause, location, skipStack),
        _zkStatus(zkStatus)
    { }
    VESPA_DEFINE_EXCEPTION_SPINE(ZKGenericException);
private:
    const int _zkStatus;
};

class ZKFacade : public std::enable_shared_from_this<ZKFacade> {
    volatile bool _retriesEnabled;
    volatile bool _watchersEnabled;

    zhandle_t* _zhandle;
    const static int _zkSessionTimeOut = 30 * 1000;
    const static size_t _maxDataSize = 1024 * 1024;

    class ZKWatcher;
    static void stateWatchingFun(zhandle_t*, int type, int state, const char* path, void* context);
public:
    typedef std::shared_ptr<ZKFacade> SP;

    /* Lifetime is managed by ZKFacade.
       Derived classes should only contain weak_ptrs to other objects
       to avoid linking their lifetime to the ZKFacade lifetime.
     */
    class NodeChangedWatcher {
      public:
        NodeChangedWatcher(const NodeChangedWatcher &) = delete;
        NodeChangedWatcher & operator = (const NodeChangedWatcher &) = delete;
        NodeChangedWatcher() = default;
        virtual ~NodeChangedWatcher() {};
        virtual void operator()() = 0;
    };

    typedef std::shared_ptr<NodeChangedWatcher> NodeChangedWatcherSP;

    ZKFacade(const ZKFacade &) = delete;
    ZKFacade & operator = (const ZKFacade &) = delete;
    ZKFacade(const std::string& zkservers, bool allowDNSFailure);
    ~ZKFacade();

    bool hasNode(const Path&);
    bool hasNode(const Path&, const NodeChangedWatcherSP&);

    const std::string getString(const Path&);
    Buffer getData(const Path&);  //throws ZKNodeDoesNotExistsException
    //if watcher is specified, it will be set even if the node does not exists
    Buffer getData(const Path&, const NodeChangedWatcherSP&);  //throws ZKNodeDoesNotExistsException

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

    static std::string getValidZKServers(const std::string &input, bool ignoreDNSFailure);

private:
    class RegistrationGuard {
    public:
        RegistrationGuard & operator = (const RegistrationGuard &) = delete;
        RegistrationGuard(const RegistrationGuard &) = delete;
        RegistrationGuard(ZKFacade & zk, const NodeChangedWatcherSP & watcher) : _zk(zk), _watcherContext(_zk.registerWatcher(watcher)) { }
        ~RegistrationGuard() {
            if (_watcherContext) {
                _zk.unregisterWatcher(_watcherContext);
            }
        }
        void * get() { return _watcherContext; }
        void release() { _watcherContext = nullptr; }
    private:
        ZKFacade & _zk;
        void     * _watcherContext;
    };
    void* registerWatcher(const NodeChangedWatcherSP &); //returns watcherContext
    std::shared_ptr<ZKWatcher> unregisterWatcher(void* watcherContext);
    void invokeWatcher(void* watcherContext);

    std::mutex _watchersMutex;
    typedef std::map<void*, std::shared_ptr<ZKWatcher> > WatchersMap;
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

