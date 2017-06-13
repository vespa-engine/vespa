// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <zookeeper/zookeeper.h>

#include <string>
#include <map>
#include <cassert>
#include <cstring>
#include <vector>

#include <thread>
#include <atomic>
#include <boost/lexical_cast.hpp>

#include <iostream>

#include <vespa/filedistribution/common/concurrentqueue.h>

using std::map;
using std::string;
using std::vector;
using std::pair;
using std::make_pair;
using filedistribution::ConcurrentQueue;

namespace {
std::pair<string, string> parentPathAndChildName(const string& childPath)
{
    if (childPath.empty()) {
        return std::make_pair("", "");
    } else {
        assert (childPath[0] == '/');

        size_t index = childPath.find_last_of("/");
        return std::make_pair(childPath.substr(0, index), childPath.substr(index + 1));
    }
}

struct Node {
    typedef map<string, Node> Children;
    Children children;
    bool exists;
    bool ephemeral;
    vector<char> buffer;
    vector<pair<watcher_fn, void*> > watchers;

    Node()
        :exists(false),
         ephemeral(false)
    {}

    void addWatcher(watcher_fn fn, void* context) {
        if (fn)
            watchers.push_back(make_pair(fn, context));
    }

    void triggerWatches(zhandle_t* zh, const std::string& path);
};

std::shared_ptr<Node> sharedRoot;

void doNothing() { }

struct ZHandle {
    struct Worker {
        ZHandle& zhandle;

        Worker(ZHandle* parent) : zhandle(*parent) {}

        void operator()();
    };

    int sequence;

    std::shared_ptr<Node> root;
    std::atomic<bool> _closed;
    std::thread _watchersThread;
    vector<string> ephemeralNodes;

    typedef std::function<void (void)> InvokeWatcherFun;
    ConcurrentQueue<InvokeWatcherFun> watcherInvocations;

    Node& getNode(const string& path);

    Node& getParent(const string& path);

    void ephemeralNode(const string&path) {
        ephemeralNodes.push_back(path);
    }

    ZHandle() : sequence(0), _closed(false), _watchersThread(Worker(this)) {
        if (!sharedRoot)
            sharedRoot.reset(new Node());

        root = sharedRoot;
    }

    ~ZHandle() {
        std::for_each(ephemeralNodes.begin(), ephemeralNodes.end(),
                      [this] (const string & s) { zoo_delete((zhandle_t*)this, s.c_str(), 0); });
        close();
        _watchersThread.join();
    }
    void close() {
        _closed.store(true);
        watcherInvocations.push(std::ref(doNothing));
    }
};

void
ZHandle::Worker::operator()()
{
    while (! zhandle._closed.load()) {
        InvokeWatcherFun fun = zhandle.watcherInvocations.pop();
        fun();
    }
}

Node& ZHandle::getNode(const string& path)  {
    auto splittedPath = parentPathAndChildName(path);
    if (splittedPath.second.empty()) {
        return *root;
    } else {
        return getNode(splittedPath.first).children[splittedPath.second];
    }
}

Node&
ZHandle::getParent(const string& childPath)
{
    auto splittedPath = parentPathAndChildName(childPath);
    if (splittedPath.second.empty()) {
        throw "Can't get parent of root.";
    } else {
        return getNode(splittedPath.first);
    }
}

void
Node::triggerWatches(zhandle_t* zh, const std::string& path) {
    for (auto i = watchers.begin(); i != watchers.end(); ++i) {
        ((ZHandle*)zh)->watcherInvocations.push([zh, i, path] () { i->first(zh, 0, 0, path.c_str(), i->second); });
    }
    watchers.clear();
}

} //anonymous namespace

extern "C" {

ZOOAPI void zoo_set_debug_level(ZooLogLevel) {}
ZOOAPI zhandle_t *zookeeper_init(const char * host, watcher_fn fn,
                                 int recv_timeout, const clientid_t *clientid, void *context, int flags)
{
    (void)host;
    (void)fn;
    (void)recv_timeout;
    (void)clientid;
    (void)context;
    (void)flags;

    return (zhandle_t*)new ZHandle;
}

ZOOAPI int zookeeper_close(zhandle_t *zh)
{
    delete (ZHandle*)zh;
    return 0;
}

ZOOAPI int zoo_create(zhandle_t *zh, const char *pathOrPrefix, const char *value,
                      int valuelen, const struct ACL_vector *, int flags,
                      char *path_buffer, int path_buffer_len)
{
    std::string path = pathOrPrefix;
    if (flags & ZOO_SEQUENCE)
        path += boost::lexical_cast<std::string>(((ZHandle*)zh)->sequence++);

    strncpy(path_buffer, path.c_str(), path_buffer_len);
    Node& node = ((ZHandle*)zh)->getNode(path);
    node.exists = true;

    if (flags & ZOO_EPHEMERAL)
        ((ZHandle*)zh)->ephemeralNode(path);

    node.buffer.resize(valuelen);
    std::copy(value, value + valuelen, node.buffer.begin());


    node.triggerWatches(zh, path);
    ((ZHandle*)zh)->getParent(path).triggerWatches(zh,
            parentPathAndChildName(path).first);

    return 0;
}


ZOOAPI int zoo_set(zhandle_t *zh, const char *path, const char *buffer,
                   int buflen, int version) {
    (void)version;

    Node& node = ((ZHandle*)zh)->getNode(path);
    if (!node.exists)
        return ZNONODE;


    node.buffer.resize(buflen);
    std::copy(buffer, buffer + buflen, node.buffer.begin());

    node.triggerWatches(zh, path);
    return 0;
}



ZOOAPI int zoo_get_children(zhandle_t *zh, const char *path, int watch,
                            struct String_vector *strings)
{
    (void)watch;
    return zoo_wget_children(zh, path,
                             0, 0,
                             strings);
}

ZOOAPI int zoo_wget_children(zhandle_t *zh, const char *path,
                             watcher_fn watcher, void* watcherCtx,
                             struct String_vector *strings)
{
    Node& node = ((ZHandle*)zh)->getNode(path);
    strings->count = node.children.size();
    strings->data = new char*[strings->count];

    int index = 0;
    for (auto i = node.children.begin(); i != node.children.end(); ++i) {
        strings->data[index] = new char[i->first.length() + 1];
        std::strcpy(strings->data[index], &*i->first.begin());
        ++index;
    }

    node.addWatcher(watcher, watcherCtx);

    return 0;
}




ZOOAPI int zoo_delete(zhandle_t *zh, const char *path, int version)
{
    (void)version;

    std::string pathStr = path;
    int index = pathStr.find_last_of("/");

    if (pathStr.length() == 1)
        throw "Can't delete root";

    Node& parent = ((ZHandle*)zh)->getNode(pathStr.substr(0, index));
    parent.children.erase(pathStr.substr(index + 1));

    ((ZHandle*)zh)->getParent(path).triggerWatches(zh,
            parentPathAndChildName(path).first);

    return 0;
}

void zoo_set_log_stream(FILE*) {}

int deallocate_String_vector(struct String_vector *v) {
    for (int i=0; i< v->count; ++i) {
        delete[] v->data[i];
    }
    delete[] v->data;
    return 0;
}


ZOOAPI int zoo_get(zhandle_t *zh, const char *path, int watch, char *buffer,
                   int* buffer_len, struct Stat *stat)
{
    (void)watch;

    return zoo_wget(zh, path,
                    0, 0,
                    buffer, buffer_len, stat);

}

ZOOAPI int zoo_wget(zhandle_t *zh, const char *path,
                    watcher_fn watcher, void* watcherCtx,
                    char *buffer, int* buffer_len, struct Stat *)
{
    Node& node = ((ZHandle*)zh)->getNode(path);
    std::copy(node.buffer.begin(), node.buffer.end(), buffer);
    *buffer_len = node.buffer.size();

    node.addWatcher(watcher, watcherCtx);
    return 0;
}

ZOOAPI int zoo_wexists(zhandle_t *zh, const char *path,
                       watcher_fn watcher, void* watcherCtx, struct Stat *)
{
    Node& node = ((ZHandle*)zh)->getNode(path);

    node.addWatcher(watcher, watcherCtx);
    return node.exists ? ZOK : ZNONODE;
}

ZOOAPI int zoo_exists(zhandle_t *zh, const char *path, int watch, struct Stat *stat)
{
    (void)watch;
    return zoo_wexists(zh, path,
                       0, 0,
                       stat);
}




ZOOAPI ACL_vector ZOO_OPEN_ACL_UNSAFE;

ZOOAPI const int ZOO_SEQUENCE = 1;
ZOOAPI const int ZOO_EPHEMERAL = 2;
ZOOAPI const int ZOO_SESSION_EVENT = 3;
ZOOAPI const int ZOO_EXPIRED_SESSION_STATE = 4;
ZOOAPI const int ZOO_AUTH_FAILED_STATE = 5;
}
