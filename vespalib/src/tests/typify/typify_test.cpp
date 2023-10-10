// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;

struct A { static constexpr int value_from_type = 1; };
struct B { static constexpr int value_from_type = 2; };

struct MyIntA { int value; };
struct MyIntB { int value; };
struct MyIntC { int value; }; // no typifier for this type

// MyIntA -> A or B
struct TypifyMyIntA {
    template <typename T> using Result = TypifyResultType<T>;
    template <typename F> static decltype(auto) resolve(MyIntA value, F &&f) {
        if (value.value == 1) {
            return f(Result<A>());
        } else if (value.value == 2) {
            return f(Result<B>());
        }
        abort();
    }
};

// MyIntB -> TypifyResultValue<int,1> or TypifyResultValue<int,2>
struct TypifyMyIntB {
    template <int VALUE> using Result = TypifyResultValue<int,VALUE>;
    template <typename F> static decltype(auto) resolve(MyIntB value, F &&f) {
        if (value.value == 1) {
            return f(Result<1>());
        } else if (value.value == 2) {
            return f(Result<2>());
        }
        abort();
    }
};

using TX = TypifyValue<TypifyBool, TypifyMyIntA, TypifyMyIntB>;

//-----------------------------------------------------------------------------

struct GetFromType {
    template <typename T> static int invoke() { return T::value_from_type; }
};

TEST(TypifyTest, simple_type_typification_works) {
    auto res1 = typify_invoke<1,TX,GetFromType>(MyIntA{1});
    auto res2 = typify_invoke<1,TX,GetFromType>(MyIntA{2});
    EXPECT_EQ(res1, 1);
    EXPECT_EQ(res2, 2);
}

struct GetFromValue {
    template <typename R> static int invoke() { return R::value; }
};

TEST(TypifyTest, simple_value_typification_works) {
    auto res1 = typify_invoke<1,TX,GetFromValue>(MyIntB{1});
    auto res2 = typify_invoke<1,TX,GetFromValue>(MyIntB{2});
    EXPECT_EQ(res1, 1);
    EXPECT_EQ(res2, 2);
}

struct MaybeSum {
    template <typename F1, typename V1, typename F2, typename V2> static int invoke(MyIntC v3) {
        int res = 0;
        if (F1::value) {
            res += V1::value_from_type;
        }
        if (F2::value) {
            res += V2::value;
        }
        res += v3.value;
        return res;
    }
};

TEST(TypifyTest, complex_typification_works) {
    auto res1 = typify_invoke<4,TX,MaybeSum>(false, MyIntA{2}, false, MyIntB{1}, MyIntC{4});
    auto res2 = typify_invoke<4,TX,MaybeSum>(false, MyIntA{2},  true, MyIntB{1}, MyIntC{4});
    auto res3 = typify_invoke<4,TX,MaybeSum>(true,  MyIntA{2}, false, MyIntB{1}, MyIntC{4});
    auto res4 = typify_invoke<4,TX,MaybeSum>(true,  MyIntA{2},  true, MyIntB{1}, MyIntC{4});
    EXPECT_EQ(res1, 4);
    EXPECT_EQ(res2, 5);
    EXPECT_EQ(res3, 6);
    EXPECT_EQ(res4, 7);
}

struct Singleton {
    virtual int get() const = 0;
    virtual ~Singleton() {}
};

template <int A, int B>
struct MySingleton : Singleton {
    MySingleton() = default;
    MySingleton(const MySingleton &) = delete;
    MySingleton &operator=(const MySingleton &) = delete;
    int get() const override { return A + B; }
};

struct GetSingleton {
    template <typename A, typename B>
    static const Singleton &invoke() {
        static MySingleton<A::value, B::value> obj;
        return obj;
    }
};

TEST(TypifyTest, typify_invoke_can_return_object_reference) {
    const Singleton &s1 = typify_invoke<2,TX,GetSingleton>(MyIntB{1}, MyIntB{1});
    const Singleton &s2 = typify_invoke<2,TX,GetSingleton>(MyIntB{2}, MyIntB{2});
    const Singleton &s3 = typify_invoke<2,TX,GetSingleton>(MyIntB{2}, MyIntB{2});
    EXPECT_EQ(s1.get(), 2);
    EXPECT_EQ(s2.get(), 4);
    EXPECT_EQ(s3.get(), 4);
    EXPECT_NE(&s1, &s2);
    EXPECT_EQ(&s2, &s3);
}

GTEST_MAIN_RUN_ALL_TESTS()
