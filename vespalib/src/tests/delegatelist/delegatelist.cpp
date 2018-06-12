// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/delegatelist.hpp>
#include <vespa/vespalib/util/guard.h>
#include <vespa/fastos/thread.h>
#include <queue>

#include <vespa/log/log.h>
LOG_SETUP("delegatelist_test");

using namespace vespalib;

//-----------------------------------------------------------------------------

class Test : public TestApp
{
public:
    void testEmpty();
    void testAdd();
    void testRemove();
    void testOneShot();
    void testMultiSnapshot();
    void testActors();
    void testWaitSnapshots();
    void stressTest();
    int Main() override;
};

//-----------------------------------------------------------------------------

namespace {

class Handler
{
private:
    int _num;
public:
    Handler() : _num(0) {}
    void add() { _num++; }
    int getNum() { return _num; }
};

typedef DelegateList<Handler> DL;

void multicast(DL &dl) {
    for (DL::Snapshot snap(dl) ; snap.valid(); snap.next()) {
        snap.get()->add();
    }
}

void multicast_clear(DL &dl) {
    DL::Snapshot snap(dl);
    dl.clear();
    for (; snap.valid(); snap.next()) {
        snap.get()->add();
    }
}

//-----------------------------------------------------------------------------

enum {
    CMD_MULTICAST,
    CMD_MULTICAST_CLEAR,
    CMD_ADD,
    CMD_REMOVE,
    CMD_CLEAR,
    CMD_WAIT_SNAP,
    CMD_DO,
    CMD_DONE,
    CMD_EXIT
};

struct Command
{
    DL          *dl;
    int          cmd;
    int          cnt;
    Handler *handler;
    Command(DL *dl_, int cmd_, int cnt_, Handler *handler_)
        : dl(dl_), cmd(cmd_), cnt(cnt_), handler(handler_) {}
    Command(const Command &rhs)
        : dl(rhs.dl), cmd(rhs.cmd), cnt(rhs.cnt), handler(rhs.handler) {}
    Command &operator=(const Command &rhs) {
        memcpy(this, &rhs, sizeof(Command));
        return *this;
    }
    bool operator==(const Command &rhs) {
        return memcmp(this, &rhs, sizeof(Command)) == 0;
    }
};

Command
cmd_multicast(DL *dl) {
    return Command(dl, CMD_MULTICAST, 0, 0);
}

Command
cmd_multicast_clear(DL *dl) {
    return Command(dl, CMD_MULTICAST_CLEAR, 0, 0);
}

Command
cmd_add(DL *dl, Handler *handler) {
    return Command(dl, CMD_ADD, 0, handler);
}

Command
cmd_remove(DL *dl, Handler *handler) {
    return Command(dl, CMD_REMOVE, 0, handler);
}

Command
cmd_clear(DL *dl) {
    return Command(dl, CMD_CLEAR, 0, 0);
}

Command
cmd_wait_snap(DL *dl) {
    return Command(dl, CMD_WAIT_SNAP, 0, 0);
}

Command
cmd_do(int cnt) {
    return Command(0, CMD_DO, cnt, 0);
}

Command
cmd_done() {
    return Command(0, CMD_DONE, 0, 0);
}

Command
cmd_exit() {
    return Command(0, CMD_EXIT, 0, 0);
}

typedef std::vector<Command>    CmdList;
typedef std::pair<Command, int> HistEntry;
typedef std::vector<HistEntry>  HistList;

//-----------------------------------------------------------------------------

struct History {
    Lock         lock;
    HistList     list;
    History() : lock(), list() {}
    void add(const HistEntry &entry) {
        LockGuard guard(lock);
        list.push_back(entry);
    }
};

//-----------------------------------------------------------------------------

template <typename T>
class Queue {
private:
    std::queue<T> _q;
    Monitor       _cond;
    int           _waitCnt;
    Queue(const Queue &);
    Queue &operator=(const Queue &);
public:
    Queue() : _q(), _cond(), _waitCnt(0) {}
    void enqueue(const T &entry) {
        MonitorGuard guard(_cond);
        _q.push(entry);
        if (_waitCnt > 0) {
            guard.signal();
        }
    }
    T dequeue() {
        MonitorGuard guard(_cond);
        CounterGuard cntGuard(_waitCnt);
        while (_q.empty()) {
            guard.wait();
        }
        T tmp = _q.front();
        _q.pop();
        return tmp;
    }
    size_t size() const { return _q.size(); }
};

typedef Queue<CmdList> CmdListQueue;

//-----------------------------------------------------------------------------

class Actor : public FastOS_Runnable
{
public:
    enum {
        STATE_INIT,
        STATE_IDLE,
        STATE_BUSY,
        STATE_DONE
    };
private:
    int           _id;
    History      *_hist;
    CmdListQueue  _queue;
    Monitor       _cond;
    int           _state;
    int           _waitCnt;
    int           _opCnt;
    bool          _exit;
    Actor(const Actor &);
    Actor &operator=(const Actor &);
    void setState(int state, MonitorGuard &guard);
    void doneOp(const Command &cmd);
    int perform(int cnt, int start, const CmdList &cmdList);
public:
    Actor(int id, History *hist);
    ~Actor();
    int getOpCnt() const { return _opCnt; }
    int getState() const { return _state; }
    void doIt(const CmdList &cmdList);
    void doIt(const Command &cmd);
    void waitState(int state);
    void Run(FastOS_ThreadInterface *, void *) override;
};

Actor::Actor(int id, History *hist)
    : _id(id), _hist(hist), _queue(), _cond(), _state(STATE_INIT),
      _waitCnt(0), _opCnt(0), _exit(false)
{}
Actor::~Actor() {}

void
Actor::setState(int state, MonitorGuard &guard) {
    _state = state;
    if (_waitCnt > 0) {
        guard.broadcast();
    }
}


void
Actor::doneOp(const Command &cmd)
{
    ++_opCnt;
    if (_hist != 0) {
        _hist->add(HistEntry(cmd, _id));
    }
}


int
Actor::perform(int cnt, int start, const CmdList &cmdList)
{
    int doneIdx = cmdList.size();
    for (int i = 0; i < cnt; ++i) {
        for (uint32_t idx = start; idx < cmdList.size(); ++idx) {
            Command cmd = cmdList[idx];
            switch (cmd.cmd) {
            case CMD_MULTICAST:
                multicast(*cmd.dl);
                doneOp(cmd);
                break;
            case CMD_MULTICAST_CLEAR:
                multicast_clear(*cmd.dl);
                doneOp(cmd);
                break;
            case CMD_ADD:
                cmd.dl->add(cmd.handler);
                doneOp(cmd);
                break;
            case CMD_REMOVE:
                cmd.dl->remove(cmd.handler);
                doneOp(cmd);
                break;
            case CMD_CLEAR:
                cmd.dl->clear();
                doneOp(cmd);
                break;
            case CMD_WAIT_SNAP:
                cmd.dl->waitSnapshots();
                doneOp(cmd);
                break;
            case CMD_DO:
                idx = perform(cmd.cnt, idx + 1, cmdList);
                break;
            case CMD_DONE:
                doneIdx = idx;
                idx = cmdList.size();
                break;
            case CMD_EXIT:
                _exit = true;
                return cmdList.size();
                break;
            default:
                LOG_ABORT("should not be reached"); // that does not seem to work
            }
        }
    }
    return doneIdx;
}


void
Actor::doIt(const CmdList &cmdList)
{
    MonitorGuard guard(_cond);
    setState(STATE_BUSY, guard);
    _queue.enqueue(cmdList);
}


void
Actor::doIt(const Command &cmd)
{
    CmdList cmdList;
    cmdList.push_back(cmd);
    doIt(cmdList);
}


void
Actor::waitState(int state) {
    MonitorGuard guard(_cond);
    CounterGuard cntGuard(_waitCnt);
    while (_state != state) {
        guard.wait();
    }
}


void
Actor::Run(FastOS_ThreadInterface *, void *)
{
    while (!_exit) {
        {
            MonitorGuard guard(_cond);
            if (_queue.size() == 0) {
                setState(STATE_IDLE, guard);
            }
        }
        CmdList cmdList = _queue.dequeue();
        perform(1, 0, cmdList);
    }
    {
        MonitorGuard guard(_cond);
        setState(STATE_DONE, guard);
    }
}

} // namespace <unnamed>

//-----------------------------------------------------------------------------

void
Test::testEmpty()
{
    DL multicaster;
    multicast(multicaster);
    multicast_clear(multicaster);
    DL::Snapshot empty_snap(multicaster);
    EXPECT_TRUE(!empty_snap.valid());
}


void
Test::testAdd()
{
    DL multicaster;
    Handler h1;
    Handler h2;
    Handler h3;
    Handler h4;
    Handler h5;

    // ensure correct initial state
    EXPECT_TRUE(h1.getNum() == 0);
    EXPECT_TRUE(h2.getNum() == 0);
    EXPECT_TRUE(h3.getNum() == 0);
    EXPECT_TRUE(h4.getNum() == 0);
    EXPECT_TRUE(h5.getNum() == 0);

    // test basic adding
    multicaster.add(&h1);
    multicast(multicaster);
    multicaster.add(&h2);
    multicast(multicaster);
    multicaster.add(&h3);
    multicast(multicaster);
    multicaster.add(&h4);
    multicast(multicaster);
    multicaster.add(&h5);
    multicast(multicaster);
    EXPECT_TRUE(h1.getNum() == 5);
    EXPECT_TRUE(h2.getNum() == 4);
    EXPECT_TRUE(h3.getNum() == 3);
    EXPECT_TRUE(h4.getNum() == 2);
    EXPECT_TRUE(h5.getNum() == 1);

    // duplicate adds
    multicaster.add(&h1);
    multicaster.add(&h1);
    multicaster.add(&h1);
    multicast(multicaster);
    EXPECT_TRUE(h1.getNum() == 6);
    EXPECT_TRUE(h2.getNum() == 5);
    EXPECT_TRUE(h3.getNum() == 4);
    EXPECT_TRUE(h4.getNum() == 3);
    EXPECT_TRUE(h5.getNum() == 2);
}


void
Test::testRemove()
{
    DL multicaster;
    Handler h1;
    Handler h2;
    Handler h3;
    Handler h4;
    Handler h5;

    multicaster.add(&h1).add(&h2).add(&h3).add(&h4).add(&h5);
    multicast(multicaster);
    EXPECT_TRUE(h1.getNum() == 1);
    EXPECT_TRUE(h2.getNum() == 1);
    EXPECT_TRUE(h3.getNum() == 1);
    EXPECT_TRUE(h4.getNum() == 1);
    EXPECT_TRUE(h5.getNum() == 1);

    // remove middle
    multicaster.remove(&h3);
    multicast(multicaster);
    EXPECT_TRUE(h1.getNum() == 2);
    EXPECT_TRUE(h2.getNum() == 2);
    EXPECT_TRUE(h3.getNum() == 1);
    EXPECT_TRUE(h4.getNum() == 2);
    EXPECT_TRUE(h5.getNum() == 2);

    // remove head
    multicaster.remove(&h1);
    multicast(multicaster);
    EXPECT_TRUE(h1.getNum() == 2);
    EXPECT_TRUE(h2.getNum() == 3);
    EXPECT_TRUE(h3.getNum() == 1);
    EXPECT_TRUE(h4.getNum() == 3);
    EXPECT_TRUE(h5.getNum() == 3);

    // remove tail
    multicaster.remove(&h5);
    multicast(multicaster);
    EXPECT_TRUE(h1.getNum() == 2);
    EXPECT_TRUE(h2.getNum() == 4);
    EXPECT_TRUE(h3.getNum() == 1);
    EXPECT_TRUE(h4.getNum() == 4);
    EXPECT_TRUE(h5.getNum() == 3);

    // duplicate removes
    multicaster.remove(&h1).remove(&h3).remove(&h5);
    multicast(multicaster);
    EXPECT_TRUE(h1.getNum() == 2);
    EXPECT_TRUE(h2.getNum() == 5);
    EXPECT_TRUE(h3.getNum() == 1);
    EXPECT_TRUE(h4.getNum() == 5);
    EXPECT_TRUE(h5.getNum() == 3);

    // remove all
    multicaster.clear();
    multicast(multicaster);
    EXPECT_TRUE(h1.getNum() == 2);
    EXPECT_TRUE(h2.getNum() == 5);
    EXPECT_TRUE(h3.getNum() == 1);
    EXPECT_TRUE(h4.getNum() == 5);
    EXPECT_TRUE(h5.getNum() == 3);
}


void
Test::testOneShot()
{
    DL multicaster;
    Handler h1;
    Handler h2;
    Handler h3;
    Handler h4;
    Handler h5;

    // oneshot multicast removes handlers
    multicaster.add(&h1).add(&h2).add(&h3).add(&h4).add(&h5);
    multicast_clear(multicaster);
    multicast(multicaster);
    EXPECT_TRUE(h1.getNum() == 1);
    EXPECT_TRUE(h2.getNum() == 1);
    EXPECT_TRUE(h3.getNum() == 1);
    EXPECT_TRUE(h4.getNum() == 1);
    EXPECT_TRUE(h5.getNum() == 1);
}


void
Test::testMultiSnapshot()
{
    DL multicaster;
    Handler h1;
    Handler h2;
    Handler h3;
    Handler h4;
    Handler h5;

    DL::Snapshot empty_snap(multicaster);
    multicaster.add(&h1).add(&h2).add(&h3).add(&h4).add(&h5);
    DL::Snapshot snap1(multicaster);
    multicaster.remove(&h3);
    DL::Snapshot snap2(multicaster);
    multicaster.remove(&h1);
    DL::Snapshot snap3(multicaster);
    multicaster.remove(&h5);
    DL::Snapshot snap4(multicaster);

    EXPECT_TRUE(!empty_snap.valid());
    for (; snap1.valid(); snap1.next()) {
        snap1.get()->add();
    }
    EXPECT_TRUE(h1.getNum() == 1);
    EXPECT_TRUE(h2.getNum() == 1);
    EXPECT_TRUE(h3.getNum() == 1);
    EXPECT_TRUE(h4.getNum() == 1);
    EXPECT_TRUE(h5.getNum() == 1);
    for (; snap2.valid(); snap2.next()) {
        snap2.get()->add();
    }
    EXPECT_TRUE(h1.getNum() == 2);
    EXPECT_TRUE(h2.getNum() == 2);
    EXPECT_TRUE(h3.getNum() == 1);
    EXPECT_TRUE(h4.getNum() == 2);
    EXPECT_TRUE(h5.getNum() == 2);
    for (; snap3.valid(); snap3.next()) {
        snap3.get()->add();
    }
    EXPECT_TRUE(h1.getNum() == 2);
    EXPECT_TRUE(h2.getNum() == 3);
    EXPECT_TRUE(h3.getNum() == 1);
    EXPECT_TRUE(h4.getNum() == 3);
    EXPECT_TRUE(h5.getNum() == 3);
    for (; snap4.valid(); snap4.next()) {
        snap4.get()->add();
    }
    EXPECT_TRUE(h1.getNum() == 2);
    EXPECT_TRUE(h2.getNum() == 4);
    EXPECT_TRUE(h3.getNum() == 1);
    EXPECT_TRUE(h4.getNum() == 4);
    EXPECT_TRUE(h5.getNum() == 3);
}


void
Test::testActors()
{
    FastOS_ThreadPool pool(65000);
    History           hist;
    Actor             a1(1, &hist);
    Actor             a2(2, &hist);
    DL                dl;
    Handler           h1;
    Handler           h2;

    ASSERT_TRUE(pool.NewThread(&a1, 0) != 0);
    ASSERT_TRUE(pool.NewThread(&a2, 0) != 0);

    {
        CmdList prog;
        prog.push_back(cmd_add(&dl, &h1));
        prog.push_back(cmd_multicast(&dl));
        prog.push_back(cmd_add(&dl, &h2));
        prog.push_back(cmd_multicast(&dl));
        a1.doIt(prog);
        a1.waitState(Actor::STATE_IDLE);
    }

    EXPECT_TRUE(h1.getNum() == 2);
    EXPECT_TRUE(h2.getNum() == 1);

    {
        CmdList prog;
        prog.push_back(cmd_remove(&dl, &h1));
        prog.push_back(cmd_multicast(&dl));
        prog.push_back(cmd_clear(&dl));
        prog.push_back(cmd_multicast(&dl));
        a2.doIt(prog);
        a2.waitState(Actor::STATE_IDLE);
    }

    EXPECT_TRUE(h1.getNum() == 2);
    EXPECT_TRUE(h2.getNum() == 2);

    {
        CmdList prog;
        prog.push_back(cmd_add(&dl, &h1));
        prog.push_back(cmd_add(&dl, &h2));
        prog.push_back(cmd_multicast_clear(&dl));
        prog.push_back(cmd_multicast(&dl));
        a1.doIt(prog);
        a1.waitState(Actor::STATE_IDLE);
    }

    EXPECT_TRUE(h1.getNum() == 3);
    EXPECT_TRUE(h2.getNum() == 3);

    {
        CmdList prog;
        prog.push_back(cmd_add(&dl, &h1));
        prog.push_back(cmd_add(&dl, &h2));
        prog.push_back(cmd_do(10));
        prog.push_back(cmd_do(10));
        prog.push_back(cmd_multicast(&dl));
        prog.push_back(cmd_done());
        prog.push_back(cmd_done());
        prog.push_back(cmd_exit());
        a2.doIt(prog);
        a2.waitState(Actor::STATE_DONE);
    }

    EXPECT_TRUE(h1.getNum() == 103);
    EXPECT_TRUE(h2.getNum() == 103);

    EXPECT_TRUE(hist.list.size() == 114);

    EXPECT_TRUE(hist.list[0].first == cmd_add(&dl, &h1));
    EXPECT_TRUE(hist.list[1].first == cmd_multicast(&dl));
    EXPECT_TRUE(hist.list[2].first == cmd_add(&dl, &h2));
    EXPECT_TRUE(hist.list[3].first == cmd_multicast(&dl));
    for (int i = 0; i < 4; i++) {
        EXPECT_TRUE(hist.list[i].second == 1);
    }

    EXPECT_TRUE(hist.list[4].first == cmd_remove(&dl, &h1));
    EXPECT_TRUE(hist.list[5].first == cmd_multicast(&dl));
    EXPECT_TRUE(hist.list[6].first == cmd_clear(&dl));
    EXPECT_TRUE(hist.list[7].first == cmd_multicast(&dl));
    for (int i = 4; i < 8; i++) {
        EXPECT_TRUE(hist.list[i].second == 2);
    }

    EXPECT_TRUE(hist.list[8].first == cmd_add(&dl, &h1));
    EXPECT_TRUE(hist.list[9].first == cmd_add(&dl, &h2));
    EXPECT_TRUE(hist.list[10].first == cmd_multicast_clear(&dl));
    EXPECT_TRUE(hist.list[11].first == cmd_multicast(&dl));
    for (int i = 8; i < 12; i++) {
        EXPECT_TRUE(hist.list[i].second == 1);
    }

    EXPECT_TRUE(hist.list[12].first == cmd_add(&dl, &h1));
    EXPECT_TRUE(hist.list[13].first == cmd_add(&dl, &h2));
    EXPECT_TRUE(hist.list[12].second == 2);
    EXPECT_TRUE(hist.list[13].second == 2);

    for (int i = 14; i < 114; i++) {
        EXPECT_TRUE(hist.list[i].first == cmd_multicast(&dl));
        EXPECT_TRUE(hist.list[i].second == 2);
    }

    a1.doIt(cmd_exit());
    a1.waitState(Actor::STATE_DONE);

    EXPECT_TRUE(a1.getOpCnt() == 8);
    EXPECT_TRUE(a2.getOpCnt() == 106);
}


void
Test::stressTest()
{
    FastOS_ThreadPool pool(65000);
    Actor             a1(1, 0);
    Actor             a2(2, 0);
    Actor             a3(3, 0);
    Actor             a4(4, 0);
    Actor             a5(5, 0);
    Actor             a6(6, 0);
    DL                dl;
    Handler           h1;
    Handler           h2;
    Handler           h3;
    Handler           h4;
    Handler           h5;
    int               scale = 10000;

    ASSERT_TRUE(pool.NewThread(&a1, 0) != 0);
    ASSERT_TRUE(pool.NewThread(&a2, 0) != 0);
    ASSERT_TRUE(pool.NewThread(&a3, 0) != 0);
    ASSERT_TRUE(pool.NewThread(&a4, 0) != 0);
    ASSERT_TRUE(pool.NewThread(&a5, 0) != 0);
    ASSERT_TRUE(pool.NewThread(&a6, 0) != 0);

    CmdList prog_multicast;
    prog_multicast.push_back(cmd_do(10 * scale));
    prog_multicast.push_back(cmd_multicast(&dl));
    prog_multicast.push_back(cmd_done());
    prog_multicast.push_back(cmd_exit());

    CmdList prog_wait_snap;
    prog_wait_snap.push_back(cmd_do(10 * scale));
    prog_wait_snap.push_back(cmd_wait_snap(&dl));
    prog_wait_snap.push_back(cmd_done());
    prog_wait_snap.push_back(cmd_exit());

    CmdList prog_add_remove_1;
    prog_add_remove_1.push_back(cmd_do(scale));
    prog_add_remove_1.push_back(cmd_add(&dl, &h1));
    prog_add_remove_1.push_back(cmd_add(&dl, &h3));
    prog_add_remove_1.push_back(cmd_remove(&dl, &h2));
    prog_add_remove_1.push_back(cmd_remove(&dl, &h4));
    prog_add_remove_1.push_back(cmd_add(&dl, &h4));
    prog_add_remove_1.push_back(cmd_add(&dl, &h2));
    prog_add_remove_1.push_back(cmd_remove(&dl, &h5));
    prog_add_remove_1.push_back(cmd_remove(&dl, &h3));
    prog_add_remove_1.push_back(cmd_add(&dl, &h5));
    prog_add_remove_1.push_back(cmd_remove(&dl, &h1));
    prog_add_remove_1.push_back(cmd_done());
    prog_add_remove_1.push_back(cmd_exit());

    CmdList prog_add_remove_2;
    prog_add_remove_2.push_back(cmd_do(scale));
    prog_add_remove_2.push_back(cmd_add(&dl, &h5));
    prog_add_remove_2.push_back(cmd_add(&dl, &h4));
    prog_add_remove_2.push_back(cmd_remove(&dl, &h1));
    prog_add_remove_2.push_back(cmd_remove(&dl, &h3));
    prog_add_remove_2.push_back(cmd_add(&dl, &h1));
    prog_add_remove_2.push_back(cmd_remove(&dl, &h2));
    prog_add_remove_2.push_back(cmd_add(&dl, &h2));
    prog_add_remove_2.push_back(cmd_add(&dl, &h3));
    prog_add_remove_2.push_back(cmd_remove(&dl, &h5));
    prog_add_remove_2.push_back(cmd_remove(&dl, &h4));
    prog_add_remove_2.push_back(cmd_done());
    prog_add_remove_2.push_back(cmd_exit());

    CmdList prog_add_remove_3;
    prog_add_remove_3.push_back(cmd_do(scale));
    prog_add_remove_3.push_back(cmd_add(&dl, &h3));
    prog_add_remove_3.push_back(cmd_remove(&dl, &h4));
    prog_add_remove_3.push_back(cmd_remove(&dl, &h3));
    prog_add_remove_3.push_back(cmd_add(&dl, &h5));
    prog_add_remove_3.push_back(cmd_add(&dl, &h2));
    prog_add_remove_3.push_back(cmd_remove(&dl, &h2));
    prog_add_remove_3.push_back(cmd_add(&dl, &h1));
    prog_add_remove_3.push_back(cmd_add(&dl, &h4));
    prog_add_remove_3.push_back(cmd_remove(&dl, &h1));
    prog_add_remove_3.push_back(cmd_remove(&dl, &h5));
    prog_add_remove_3.push_back(cmd_done());
    prog_add_remove_3.push_back(cmd_exit());

    a1.doIt(prog_multicast);
    a2.doIt(prog_multicast);
    a3.doIt(prog_wait_snap);
    a4.doIt(prog_add_remove_1);
    a5.doIt(prog_add_remove_2);
    a6.doIt(prog_add_remove_3);

    a1.waitState(Actor::STATE_DONE);
    a2.waitState(Actor::STATE_DONE);
    a3.waitState(Actor::STATE_DONE);
    a4.waitState(Actor::STATE_DONE);
    a5.waitState(Actor::STATE_DONE);
    a6.waitState(Actor::STATE_DONE);

    EXPECT_TRUE(a1.getOpCnt() == 10 * scale);
    EXPECT_TRUE(a2.getOpCnt() == 10 * scale);
    EXPECT_TRUE(a3.getOpCnt() == 10 * scale);
    EXPECT_TRUE(a4.getOpCnt() == 10 * scale);
    EXPECT_TRUE(a5.getOpCnt() == 10 * scale);
    EXPECT_TRUE(a6.getOpCnt() == 10 * scale);
}


void
Test::testWaitSnapshots()
{
    FastOS_ThreadPool pool(65000);
    Actor             a1(1, 0);
    DL                dl;
    std::unique_ptr<DL::Snapshot> s1;
    std::unique_ptr<DL::Snapshot> s2;
    ASSERT_TRUE(pool.NewThread(&a1, 0) != 0);
    s1.reset(new DL::Snapshot(dl));                 // create snap 1
    a1.doIt(cmd_wait_snap(&dl));                    // wait for snaps
    FastOS_Thread::Sleep(1000);
    EXPECT_TRUE(a1.getState() == Actor::STATE_BUSY); // still waiting...
    s2.reset(new DL::Snapshot(dl));                 // create snap 2
    s1.reset();                                     // destroy snap 1
    FastOS_Thread::Sleep(1000);
    EXPECT_TRUE(a1.getState() == Actor::STATE_IDLE); // wait done!
    a1.doIt(cmd_exit());
    a1.waitState(Actor::STATE_DONE);
    s2.reset();                                     // destroy snap 2
    EXPECT_TRUE(a1.getOpCnt() == 1);
}

//-----------------------------------------------------------------------------

struct Foo { void completeBarrier() {} };

int
Test::Main()
{
    TEST_INIT("delegatelist_test");
    LOG(info, "Lock         size: %4zu bytes", sizeof(Lock));
    LOG(info, "ArrayQueue   size: %4zu bytes", sizeof(ArrayQueue<Foo>));
    LOG(info, "std::vector  size: %4zu bytes", sizeof(std::vector<Foo>));
    LOG(info, "EventBarrier size: %4zu bytes", sizeof(EventBarrier<Foo>));
    LOG(info, "DelegateList size: %4zu bytes", sizeof(DelegateList<Foo>));

    testEmpty();
    testAdd();
    testRemove();
    testOneShot();
    testMultiSnapshot();

    TEST_FLUSH();
    testActors();
    testWaitSnapshots();

    TEST_FLUSH();
    stressTest();
    TEST_DONE();
}

TEST_APPHOOK(Test);
