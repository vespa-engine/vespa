// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>

#include <chrono>

class B;

static int  taken = 0;
extern void takeB(B* foo) __attribute__((noinline));

class A {
public:
    virtual B* asB() { return nullptr; }
    virtual ~A() = default;
};

class C : public A {
public:
    B* otherB;
    B* asB() override { return otherB; }
    C() : otherB(nullptr) {}
};

class B : public C {
public:
    B* asB() override { return this; }
};

class CastTest {
    A* myB;
    B* realB;

public:
    B* DummyCast() { return realB; }
    B* DynamicCast() { return dynamic_cast<B*>(myB); }
    B* TypesafeCast() { return myB->asB(); }
    B* UnsafeCast() { return reinterpret_cast<B*>(myB); }
    B* StaticCast() { return static_cast<B*>(myB); }

    CastTest() { myB = realB = new B; }

    ~CastTest() { delete myB; }
};

#define LOOPCNT 30000000

TEST(CastSpeedTest, cast_speed) {
    using clock = std::chrono::steady_clock;
    using ms_double = std::chrono::duration<double, std::milli>;

    clock::time_point start;
    ms_double         ms;

    CastTest casttest;

    double   actualTime;
    uint32_t i;

    taken = 0;
    start = clock::now();
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
    ms = (clock::now() - start);
    actualTime = ms.count();
    fprintf(stderr, "%d dummy cast calls: %f ms (%1.2f/us) [%f]\n", taken, ms.count(), 0.001 * taken / ms.count(),
            actualTime);

    taken = 0;
    start = clock::now();
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
    ms = (clock::now() - start);
    actualTime = ms.count();
    fprintf(stderr, "%d dynamic cast calls: %f ms (%1.2f/us) [%f]\n", taken, ms.count(), 0.001 * taken / ms.count(),
            actualTime);

    taken = 0;
    start = clock::now();
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
    ms = (clock::now() - start);
    actualTime = ms.count();
    fprintf(stderr, "%d typesafe cast calls: %f ms (%1.2f/us) [%f]\n", taken, ms.count(), 0.001 * taken / ms.count(),
            actualTime);

    taken = 0;
    start = clock::now();
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
    ms = (clock::now() - start);
    actualTime = ms.count();
    fprintf(stderr, "%d static cast calls: %f ms (%1.2f/us) [%f]\n", taken, ms.count(), 0.001 * taken / ms.count(),
            actualTime);

    taken = 0;
    start = clock::now();
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
    ms = (clock::now() - start);
    actualTime = ms.count();
    fprintf(stderr, "%d reinterpret_cast calls: %f ms (%1.2f/us) [%f]\n", taken, ms.count(),
            0.001 * taken / ms.count(), actualTime);

    taken = 0;
    start = clock::now();
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
    ms = (clock::now() - start);
    actualTime = ms.count();
    fprintf(stderr, "%d dummy cast calls: %f ms (%1.2f/us) [%f]\n", taken, ms.count(), 0.001 * taken / ms.count(),
            actualTime);
}

void takeB(B* foo) {
    if (foo != nullptr) {
        taken++;
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
