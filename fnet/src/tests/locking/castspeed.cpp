// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/fnet.h>

class B;

static int taken = 0;
extern void takeB(B* foo) __attribute__((noinline));

class A
{
public:
    virtual B* asB() { return 0; }
    virtual ~A() {}
};

class C: public A
{
public:
    B *otherB;
    B* asB() override { return otherB; }
    C() : otherB(nullptr) {}
};

class B: public C
{
public:
    B* asB() override { return this; }
};


class CastTest
{
    A* myB;
    B* realB;
public:
    B* DummyCast()   {
        return realB;
    }
    B* DynamicCast() {
        return dynamic_cast<B*>(myB);
    }
    B* TypesafeCast() {
        return myB->asB();
    }
    B* UnsafeCast() {
        return reinterpret_cast<B*>(myB);
    }
    B* StaticCast() {
        return static_cast<B*>(myB);
    }

    CastTest() {
        myB = realB = new B;
    }

    ~CastTest() {
        delete myB;
    }
};

#define LOOPCNT 30000000

TEST("cast speed") {
    FastOS_Time      start;
    FastOS_Time       stop;

    CastTest       casttest;

    double      actualTime;
    uint32_t             i;

    taken = 0;
    start.SetNow();
    for (i = 0; i < LOOPCNT; i++) {
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
    }
    stop.SetNow();
    stop -= start;
    actualTime = stop.MilliSecs();
    fprintf(stderr,
        "%d dummy cast calls: %f ms (%1.2f/us) [%f]\n",
        taken, stop.MilliSecs(),
        0.001 * taken / stop.MilliSecs(),
        actualTime);

    taken = 0;
    start.SetNow();
    for (i = 0; i < LOOPCNT; i++) {
        takeB(casttest.DynamicCast());
        takeB(casttest.DynamicCast());
        takeB(casttest.DynamicCast());
        takeB(casttest.DynamicCast());
        takeB(casttest.DynamicCast());
        takeB(casttest.DynamicCast());
        takeB(casttest.DynamicCast());
        takeB(casttest.DynamicCast());
        takeB(casttest.DynamicCast());
        takeB(casttest.DynamicCast());
    }
    stop.SetNow();
    stop -= start;
    actualTime = stop.MilliSecs();
    fprintf(stderr,
        "%d dynamic cast calls: %f ms (%1.2f/us) [%f]\n",
        taken, stop.MilliSecs(),
        0.001 * taken / stop.MilliSecs(),
        actualTime);

    taken = 0;
    start.SetNow();
    for (i = 0; i < LOOPCNT; i++) {
        takeB(casttest.TypesafeCast());
        takeB(casttest.TypesafeCast());
        takeB(casttest.TypesafeCast());
        takeB(casttest.TypesafeCast());
        takeB(casttest.TypesafeCast());
        takeB(casttest.TypesafeCast());
        takeB(casttest.TypesafeCast());
        takeB(casttest.TypesafeCast());
        takeB(casttest.TypesafeCast());
        takeB(casttest.TypesafeCast());
    }
    stop.SetNow();
    stop -= start;
    actualTime = stop.MilliSecs();
    fprintf(stderr,
        "%d typesafe cast calls: %f ms (%1.2f/us) [%f]\n",
        taken, stop.MilliSecs(),
        0.001 * taken / stop.MilliSecs(),
        actualTime);

    taken = 0;
    start.SetNow();
    for (i = 0; i < LOOPCNT; i++) {
        takeB(casttest.StaticCast());
        takeB(casttest.StaticCast());
        takeB(casttest.StaticCast());
        takeB(casttest.StaticCast());
        takeB(casttest.StaticCast());
        takeB(casttest.StaticCast());
        takeB(casttest.StaticCast());
        takeB(casttest.StaticCast());
        takeB(casttest.StaticCast());
        takeB(casttest.StaticCast());
    }
    stop.SetNow();
    stop -= start;
    actualTime = stop.MilliSecs();
    fprintf(stderr,
        "%d static cast calls: %f ms (%1.2f/us) [%f]\n",
        taken, stop.MilliSecs(),
        0.001 * taken / stop.MilliSecs(),
        actualTime);

    taken = 0;
    start.SetNow();
    for (i = 0; i < LOOPCNT; i++) {
        takeB(casttest.UnsafeCast());
        takeB(casttest.UnsafeCast());
        takeB(casttest.UnsafeCast());
        takeB(casttest.UnsafeCast());
        takeB(casttest.UnsafeCast());
        takeB(casttest.UnsafeCast());
        takeB(casttest.UnsafeCast());
        takeB(casttest.UnsafeCast());
        takeB(casttest.UnsafeCast());
        takeB(casttest.UnsafeCast());
    }
    stop.SetNow();
    stop -= start;
    actualTime = stop.MilliSecs();
    fprintf(stderr,
        "%d reinterpret_cast calls: %f ms (%1.2f/us) [%f]\n",
        taken, stop.MilliSecs(),
        0.001 * taken / stop.MilliSecs(),
        actualTime);

    taken = 0;
    start.SetNow();
    for (i = 0; i < LOOPCNT; i++) {
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
        takeB(casttest.DummyCast());
    }
    stop.SetNow();
    stop -= start;
    actualTime = stop.MilliSecs();
    fprintf(stderr,
        "%d dummy cast calls: %f ms (%1.2f/us) [%f]\n",
        taken, stop.MilliSecs(),
        0.001 * taken / stop.MilliSecs(),
        actualTime);
}

void takeB(B* foo)
{
    if (foo != 0) {
        taken++;
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
