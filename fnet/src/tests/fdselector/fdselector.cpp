// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/fnet.h>


struct Handler : public FNET_IFDSelectorHandler
{
    int readEventCnt[2];
    int writeEventCnt[2];

    Handler()
        : readEventCnt(),
          writeEventCnt()
    {
        reset();
    }
    void readEvent(FNET_FDSelector *src) override
    {
        readEventCnt[src->getContext()._value.INT]++;
    }
    void writeEvent(FNET_FDSelector *src) override
    {
        writeEventCnt[src->getContext()._value.INT]++;
    }
    bool empty()
    {
        return (   readEventCnt[0]  == 0
                && readEventCnt[1]  == 0
                && writeEventCnt[0] == 0
                && writeEventCnt[1] == 0);
    }
    void reset()
    {
        readEventCnt[0]  = 0;
        readEventCnt[1]  = 0;
        writeEventCnt[0] = 0;
        writeEventCnt[1] = 0;
    }
};


struct State
{
    int              pipefd[2];
    FNET_Transport   transport;
    Handler          handler;

    void eventLoop(int cnt)
    {
        for (int i = 0; i < cnt; ++i) {
            transport.EventLoopIteration();
        }
    }
    bool checkEmpty()
    {
        eventLoop(1);
        return handler.empty();
    }
    void shutDown()
    {
        transport.ShutDown(false);
        for (;;) {
            if (!transport.EventLoopIteration()) {
                return;
            }
        }
    }
    State() : pipefd(), transport(), handler() {
        pipefd[0] = -1;
        pipefd[1] = -1;
        ASSERT_TRUE(pipe(pipefd) == 0);
        ASSERT_TRUE(transport.InitEventLoop());
        ASSERT_TRUE(handler.empty());
    }
    ~State() {
        shutDown();
    }
};


struct Selector : public FNET_FDSelector
{
    static FastOS_Mutex mutex;
    static int ctorCnt;
    static int dtorCnt;

    Selector(State &state, uint32_t idx)
        : FNET_FDSelector(&state.transport, state.pipefd[idx],
                          &state.handler, FNET_Context(idx))
    {
        mutex.Lock();
        ctorCnt++;
        mutex.Unlock();
    }
    ~Selector()
    {
        mutex.Lock();
        dtorCnt++;
        mutex.Unlock();
    }
};

FastOS_Mutex Selector::mutex;
int Selector::ctorCnt = 0;
int Selector::dtorCnt = 0;


TEST_F("testEmptySelection", State()) {
    State &state = f1;
    Selector *sel_0 = new Selector(state, 0);
    Selector *sel_1 = new Selector(state, 1);

    state.eventLoop(5);
    EXPECT_TRUE(state.handler.empty());

    sel_0->dispose();
    sel_1->dispose();
}


TEST_F("testWriteEvent", State()) {
    State &state = f1;
    Selector *sel = new Selector(state, 1);

    sel->updateWriteSelection(true);
    state.eventLoop(10);
    EXPECT_TRUE(state.handler.writeEventCnt[1] > 7);
    state.handler.writeEventCnt[1] = 0;
    EXPECT_TRUE(state.handler.empty());

    sel->dispose();
    EXPECT_TRUE(state.checkEmpty());
}


TEST_F("testReadEvent", State()) {
    State &state = f1;
    char buf[16];
    char buf2[16];
    strcpy(buf, "test");
    strcpy(buf2, "bogus");

    Selector *sel = new Selector(state, 0);

    sel->updateReadSelection(true);
    EXPECT_TRUE(state.checkEmpty());
    EXPECT_TRUE(state.checkEmpty());
    EXPECT_TRUE(state.checkEmpty());

    int res = write(state.pipefd[1], buf, 5);
    EXPECT_TRUE(res == 5);

    state.eventLoop(10);
    EXPECT_TRUE(state.handler.readEventCnt[0] > 7);
    state.handler.readEventCnt[0] = 0;
    EXPECT_TRUE(state.handler.empty());

    res = read(state.pipefd[0], buf2, 10);
    EXPECT_TRUE(res == 5);
    EXPECT_TRUE(strcmp(buf, buf2) == 0);

    state.eventLoop(10);
    EXPECT_TRUE(state.handler.readEventCnt[0] < 4);
    state.handler.readEventCnt[0] = 0;
    EXPECT_TRUE(state.handler.empty());

    sel->dispose();
    EXPECT_TRUE(state.checkEmpty());
}


TEST_F("testDispose", State()) {
    State &state = f1;
    Selector *sel = new Selector(state, 1);

    sel->updateWriteSelection(true);
    state.eventLoop(10);
    EXPECT_TRUE(state.handler.writeEventCnt[1] > 7);
    state.handler.writeEventCnt[1] = 0;
    EXPECT_TRUE(state.handler.empty());

    sel->dispose();
    EXPECT_TRUE(state.checkEmpty());
}


TEST_F("testToggleEvent", State()) {
    State &state = f1;
    Selector *sel = new Selector(state, 1);

    sel->updateWriteSelection(true);
    state.eventLoop(10);
    EXPECT_TRUE(state.handler.writeEventCnt[1] > 7);
    state.handler.writeEventCnt[1] = 0;
    EXPECT_TRUE(state.handler.empty());

    sel->updateWriteSelection(false);
    state.eventLoop(10);
    EXPECT_TRUE(state.handler.writeEventCnt[1] < 4);
    state.handler.writeEventCnt[1] = 0;
    EXPECT_TRUE(state.handler.empty());

    sel->updateWriteSelection(true);
    state.eventLoop(10);
    EXPECT_TRUE(state.handler.writeEventCnt[1] > 7);
    state.handler.writeEventCnt[1] = 0;
    EXPECT_TRUE(state.handler.empty());

    sel->dispose();
    EXPECT_TRUE(state.checkEmpty());
}

TEST_MAIN() {
    ASSERT_TRUE(Selector::ctorCnt == 0);
    ASSERT_TRUE(Selector::dtorCnt == 0);
    TEST_RUN_ALL();
    EXPECT_TRUE(Selector::ctorCnt > 0);
    EXPECT_TRUE(Selector::dtorCnt > 0);
    EXPECT_TRUE(Selector::ctorCnt == Selector::dtorCnt);
}
