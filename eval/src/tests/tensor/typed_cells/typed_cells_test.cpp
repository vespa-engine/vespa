// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/arrayref.h>
#include <memory>

using namespace vespalib;

//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
// Low-level typed cells reference
//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------

enum class CellType : char { DOUBLE, FLOAT, INT };
template <typename T> bool check_type(CellType type);
template <> bool check_type<double>(CellType type) { return (type == CellType::DOUBLE); }
template <> bool check_type<float>(CellType type) { return (type == CellType::FLOAT); }
template <> bool check_type<int>(CellType type) { return (type == CellType::INT); }

struct TypedCells {
    const void *data;
    CellType type;
    size_t size:56;
    explicit TypedCells(ConstArrayRef<double> cells) : data(cells.begin()), type(CellType::DOUBLE), size(cells.size()) {}
    explicit TypedCells(ConstArrayRef<float> cells) : data(cells.begin()), type(CellType::FLOAT), size(cells.size()) {}
    explicit TypedCells(ConstArrayRef<int> cells) : data(cells.begin()), type(CellType::INT), size(cells.size()) {}
    template <typename T> bool check_type() const { return ::check_type<T>(type); }
    template <typename T> ConstArrayRef<T> typify() const {
        assert(check_type<T>());
        return ConstArrayRef<T>((const T *)data, size);        
    }
    template <typename T> ConstArrayRef<T> unsafe_typify() const {
        return ConstArrayRef<T>((const T *)data, size);
    }
};

TEST("require that structures are of expected size") {
    EXPECT_EQUAL(sizeof(void*), 8u);
    EXPECT_EQUAL(sizeof(size_t), 8u);
    EXPECT_EQUAL(sizeof(CellType), 1u);
    EXPECT_EQUAL(sizeof(TypedCells), 16u);
}

//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
// CASE STUDY: Direct dispatch, minimal runtime type resolving
//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------

struct CellwiseAdd {
    template <typename A, typename B, typename C>
    static void call(const ConstArrayRef<A> &a, const ConstArrayRef<B> &b, const ConstArrayRef<C> &c, size_t cnt) __attribute__ ((noinline));
};

template <typename A, typename B, typename C>
void CellwiseAdd::call(const ConstArrayRef<A> &a, const ConstArrayRef<B> &b, const ConstArrayRef<C> &c, size_t cnt) {
    auto dst = unconstify(c);
    for (size_t i = 0; i < cnt; ++i) {
        dst[i] = a[i] + b[i];
    }
}

//-----------------------------------------------------------------------------

struct DotProduct {
    template <typename A, typename B>
    static double call(const ConstArrayRef<A> &a, const ConstArrayRef<B> &b, size_t cnt) __attribute__ ((noinline));
};

template <typename A, typename B>
double DotProduct::call(const ConstArrayRef<A> &a, const ConstArrayRef<B> &b, size_t cnt) {
    double result = 0.0;
    for (size_t i = 0; i < cnt; ++i) {
        result += (a[i] * b[i]);
    }
    return result;
}

//-----------------------------------------------------------------------------

struct Sum {
    template <typename A>
    static double call(const ConstArrayRef<A> &a) __attribute__ ((noinline));    
};

template <typename A>
double Sum::call(const ConstArrayRef<A> &a) {
    double result = 0.0;
    for (const auto &value: a) {
        result += value;
    }
    return result;
}

//-----------------------------------------------------------------------------

template <typename T>
struct Typify {
    template <typename... Args>
    static auto typify_1(const TypedCells &a, Args &&...args) {
        switch(a.type) {
        case CellType::DOUBLE: return T::call(a.unsafe_typify<double>(), std::forward<Args>(args)...);
        case CellType::FLOAT: return T::call(a.unsafe_typify<float>(), std::forward<Args>(args)...);
        case CellType::INT: return T::call(a.unsafe_typify<int>(), std::forward<Args>(args)...);
        }
        abort();
    }
    template <typename A, typename... Args>
    static auto typify_2(A &&a, const TypedCells &b, Args &&...args) {
        switch(b.type) {
        case CellType::DOUBLE: return T::call(std::forward<A>(a), b.unsafe_typify<double>(), std::forward<Args>(args)...);
        case CellType::FLOAT: return T::call(std::forward<A>(a), b.unsafe_typify<float>(), std::forward<Args>(args)...);
        case CellType::INT: return T::call(std::forward<A>(a), b.unsafe_typify<int>(), std::forward<Args>(args)...);
        }
        abort();
    }
    template <typename A, typename B, typename... Args>
    static auto typify_3(A &&a, B &&b, const TypedCells &c, Args &&...args) {
        switch(c.type) {
        case CellType::DOUBLE: return T::call(std::forward<A>(a), std::forward<B>(b), c.unsafe_typify<double>(), std::forward<Args>(args)...);
        case CellType::FLOAT: return T::call(std::forward<A>(a), std::forward<B>(b), c.unsafe_typify<float>(), std::forward<Args>(args)...);
        case CellType::INT: return T::call(std::forward<A>(a), std::forward<B>(b), c.unsafe_typify<int>(), std::forward<Args>(args)...);
        }
        abort();
    }
};

template <typename Fun>
struct Dispatch3 {
    using Self = Dispatch3<Fun>;
    template <typename A, typename B, typename C, typename... Args>
    static auto call(const ConstArrayRef<A> &a, const ConstArrayRef<B> &b, const ConstArrayRef<C> &c, Args &&...args) {
        return Fun::call(a, b, c, std::forward<Args>(args)...);
    }
    template <typename A, typename B, typename... Args>
    static auto call(const ConstArrayRef<A> &a, const ConstArrayRef<B> &b, const TypedCells &c, Args &&...args) {
        return Typify<Self>::typify_3(a, b, c, std::forward<Args>(args)...);
    }
    template <typename A, typename... Args>
    static auto call(const ConstArrayRef<A> &a, const TypedCells &b, const TypedCells &c, Args &&...args) {
        return Typify<Self>::typify_2(a, b, c, std::forward<Args>(args)...);
    }
    template <typename A, typename C, typename... Args>
    static auto call(const ConstArrayRef<A> &a, const TypedCells &b, const ConstArrayRef<C> &c, Args &&...args) {
        return Typify<Self>::typify_2(a, b, c, std::forward<Args>(args)...);
    }
    template <typename... Args>
    static auto call(const TypedCells &a, const TypedCells &b, const TypedCells &c, Args &&...args) {
        return Typify<Self>::typify_1(a, b, c, std::forward<Args>(args)...);
    }
    template <typename B, typename... Args>
    static auto call(const TypedCells &a, const ConstArrayRef<B> &b, const TypedCells &c, Args &&...args) {
        return Typify<Self>::typify_1(a, b, c, std::forward<Args>(args)...);
    }
    template <typename C, typename... Args>
    static auto call(const TypedCells &a, const TypedCells &b, const ConstArrayRef<C> &c, Args &&...args) {
        return Typify<Self>::typify_1(a, b, c, std::forward<Args>(args)...);
    }
    template <typename B, typename C, typename... Args>
    static auto call(const TypedCells &a, const ConstArrayRef<B> &b, const ConstArrayRef<C> &c, Args &&...args) {
        return Typify<Self>::typify_1(a, b, c, std::forward<Args>(args)...);
    }
};

template <typename Fun>
struct Dispatch2 {
    using Self = Dispatch2<Fun>;
    template <typename A, typename B, typename... Args>
    static auto call(const ConstArrayRef<A> &a, const ConstArrayRef<B> &b, Args &&...args) {
        return Fun::call(a, b, std::forward<Args>(args)...);
    }
    template <typename A, typename... Args>
    static auto call(const ConstArrayRef<A> &a, const TypedCells &b, Args &&...args) {
        return Typify<Self>::typify_2(a, b, std::forward<Args>(args)...);
    }
    template <typename... Args>
    static auto call(const TypedCells &a, const TypedCells &b, Args &&...args) {
        return Typify<Self>::typify_1(a, b, std::forward<Args>(args)...);
    }
    template <typename B, typename... Args>
    static auto call(const TypedCells &a, const ConstArrayRef<B> &b, Args &&...args) {
        return Typify<Self>::typify_1(a, b, std::forward<Args>(args)...);
    }
};

template <typename Fun>
struct Dispatch1 {
    using Self = Dispatch1<Fun>;
    template <typename A, typename... Args>
    static auto call(const ConstArrayRef<A> &a, Args &&...args) {
        return Fun::call(a, std::forward<Args>(args)...);
    }
    template <typename... Args>
    static auto call(const TypedCells &a, Args &&...args) {
        return Typify<Self>::typify_1(a, std::forward<Args>(args)...);
    }
};

//-----------------------------------------------------------------------------

TEST("require that direct dispatch 'a op b -> c' works") {
    std::vector<int>      a({1,2,3});
    std::vector<float>    b({1.5,2.5,3.5});
    std::vector<double>   c(3, 0.0);
    ConstArrayRef<int>    a_ref(a);
    ConstArrayRef<float>  b_ref(b);
    ConstArrayRef<double> c_ref(c);
    TypedCells            a_cells(a);
    TypedCells            b_cells(b);
    TypedCells            c_cells(c);

    Dispatch3<CellwiseAdd>::call(a_cells, b_cells, c_cells, 3);
    Dispatch3<CellwiseAdd>::call(a_cells, b_ref, c_cells, 3);
    Dispatch3<CellwiseAdd>::call(a_cells, b_cells, c_ref, 3);
    Dispatch3<CellwiseAdd>::call(a_cells, b_ref, c_ref, 3);
    Dispatch3<CellwiseAdd>::call(a_ref, b_cells, c_cells, 3);
    Dispatch3<CellwiseAdd>::call(a_ref, b_cells, c_ref, 3);
    Dispatch3<CellwiseAdd>::call(a_ref, b_ref, c_cells, 3);
    Dispatch3<CellwiseAdd>::call(a_ref, b_ref, c_ref, 3);

    EXPECT_EQUAL(c[0], 2.5);
    EXPECT_EQUAL(c[1], 4.5);
    EXPECT_EQUAL(c[2], 6.5);
}

TEST("require that direct dispatch 'dot product' with return value works") {
    std::vector<int>      a({1,2,3});
    std::vector<float>    b({1.5,2.5,3.5});
    ConstArrayRef<int>    a_ref(a);
    ConstArrayRef<float>  b_ref(b);
    TypedCells            a_cells(a);
    TypedCells            b_cells(b);
    double                expect = 1.5 + (2 * 2.5) + (3 * 3.5);

    EXPECT_EQUAL(expect, Dispatch2<DotProduct>::call(a_cells, b_cells, 3));
    EXPECT_EQUAL(expect, Dispatch2<DotProduct>::call(a_cells, b_ref, 3));
    EXPECT_EQUAL(expect, Dispatch2<DotProduct>::call(a_ref, b_cells, 3));
    EXPECT_EQUAL(expect, Dispatch2<DotProduct>::call(a_ref, b_ref, 3));
}

TEST("require that direct dispatch 'sum' with return value works") {
    std::vector<int>      a({1,2,3});
    ConstArrayRef<int>    a_ref(a);
    TypedCells            a_cells(a);
    double                expect = (1 + 2 + 3);

    EXPECT_EQUAL(expect, Dispatch1<Sum>::call(a_cells));
    EXPECT_EQUAL(expect, Dispatch1<Sum>::call(a_ref));
}

//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
// CASE STUDY: Pre-resolved templated subclass
//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------

struct CellwiseAdd2 {
    virtual void call(const TypedCells &a, const TypedCells &b, const TypedCells &c, size_t cnt) const = 0;
    template <typename A, typename B, typename C>
    static std::unique_ptr<CellwiseAdd2> create();
    virtual ~CellwiseAdd2() {}
};

template <typename A, typename B, typename C>
struct CellwiseAdd2Impl : CellwiseAdd2 {
    void call_impl(const ConstArrayRef<A> &a, const ConstArrayRef<B> &b, const ConstArrayRef<C> &c, size_t cnt) const {
        auto dst = unconstify(c);
        for (size_t i = 0; i < cnt; ++i) {
            dst[i] = a[i] + b[i];
        }
    }
    void call(const TypedCells &a, const TypedCells &b, const TypedCells &c, size_t cnt) const override {
        call_impl(a.unsafe_typify<A>(), b.unsafe_typify<B>(), c.unsafe_typify<C>(), cnt);
    }
};

template <typename A, typename B, typename C>
std::unique_ptr<CellwiseAdd2> CellwiseAdd2::create() {
    return std::make_unique<CellwiseAdd2Impl<A, B, C> >();
}

//-----------------------------------------------------------------------------

struct DotProduct2 {
    virtual double call(const TypedCells &a, const TypedCells &b, size_t cnt) const = 0;
    template <typename A, typename B>
    static std::unique_ptr<DotProduct2> create();
    virtual ~DotProduct2() {}
};

template <typename A, typename B>
struct DotProduct2Impl : DotProduct2 {
    double call_impl(const ConstArrayRef<A> &a, const ConstArrayRef<B> &b, size_t cnt) const {
        double result = 0.0;
        for (size_t i = 0; i < cnt; ++i) {
            result += (a[i] * b[i]);
        }
        return result;
    }
    double call(const TypedCells &a, const TypedCells &b, size_t cnt) const override {
        return call_impl(a.unsafe_typify<A>(), b.unsafe_typify<B>(), cnt);
    }
};

template <typename A, typename B>
std::unique_ptr<DotProduct2> DotProduct2::create() {
    return std::make_unique<DotProduct2Impl<A, B> >();
}

//-----------------------------------------------------------------------------

struct Sum2 {
    virtual double call(const TypedCells &a) const = 0;
    template <typename A>
    static std::unique_ptr<Sum2> create();
    virtual ~Sum2() {}
};

template <typename A>
struct Sum2Impl : Sum2 {
    double call_impl(const ConstArrayRef<A> &a) const {
        double result = 0.0;
        for (const auto &value: a) {
            result += value;
        }
        return result;
    }
    double call(const TypedCells &a) const override {
        return call_impl(a.unsafe_typify<A>());
    }
};

template <typename A>
std::unique_ptr<Sum2> Sum2::create() {
    return std::make_unique<Sum2Impl<A> >();
}

//-----------------------------------------------------------------------------

template <typename T, typename... Args>
std::unique_ptr<T> create(CellType a_type) {
    switch(a_type) {
    case CellType::DOUBLE: return T::template create<double, Args...>();
    case CellType::FLOAT:  return T::template create<float, Args...>();
    case CellType::INT:    return T::template create<int, Args...>();
    }
    abort();
}

template <typename T, typename... Args>
std::unique_ptr<T> create(CellType a_type, CellType b_type) {
    switch(b_type) {
    case CellType::DOUBLE: return create<T, double, Args...>(a_type);
    case CellType::FLOAT:  return create<T, float, Args...>(a_type);
    case CellType::INT:    return create<T, int, Args...>(a_type);
    }
    abort();
}

template <typename T>
std::unique_ptr<T> create(CellType a_type, CellType b_type, CellType c_type) {
    switch(c_type) {
    case CellType::DOUBLE: return create<T, double>(a_type, b_type);
    case CellType::FLOAT:  return create<T, float>(a_type, b_type);
    case CellType::INT:    return create<T, int>(a_type, b_type);
    }
    abort();
}

//-----------------------------------------------------------------------------

TEST("require that pre-resolved subclass 'a op b -> c' works") {
    std::vector<int>      a({1,2,3});
    std::vector<float>    b({1.5,2.5,3.5});
    std::vector<double>   c(3, 0.0);
    TypedCells            a_cells(a);
    TypedCells            b_cells(b);
    TypedCells            c_cells(c);

    auto op = create<CellwiseAdd2>(a_cells.type, b_cells.type, c_cells.type);
    op->call(a_cells, b_cells, c_cells, 3);

    EXPECT_EQUAL(c[0], 2.5);
    EXPECT_EQUAL(c[1], 4.5);
    EXPECT_EQUAL(c[2], 6.5);
}

TEST("require that pre-resolved subclass 'dot product' with return value works") {
    std::vector<int>      a({1,2,3});
    std::vector<float>    b({1.5,2.5,3.5});
    TypedCells            a_cells(a);
    TypedCells            b_cells(b);
    double                expect = 1.5 + (2 * 2.5) + (3 * 3.5);

    auto op = create<DotProduct2>(a_cells.type, b_cells.type);
    
    EXPECT_EQUAL(expect, op->call(a_cells, b_cells, 3));
}

TEST("require that pre-resolved subclass 'sum' with return value works") {
    std::vector<int>      a({1,2,3});
    TypedCells            a_cells(a);
    double                expect = (1 + 2 + 3);

    auto op = create<Sum2>(a_cells.type);

    EXPECT_EQUAL(expect, op->call(a_cells));
}

//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
// CASE STUDY: self-updating cached function pointer
//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------

template <typename T, typename... Args>
auto get_fun(CellType a_type) {
    switch(a_type) {
    case CellType::DOUBLE: return T::template get_fun<double, Args...>();
    case CellType::FLOAT:  return T::template get_fun<float, Args...>();
    case CellType::INT:    return T::template get_fun<int, Args...>();
    }
    abort();
}

template <typename T, typename... Args>
auto get_fun(CellType a_type, CellType b_type) {
    switch(b_type) {
    case CellType::DOUBLE: return get_fun<T, double, Args...>(a_type);
    case CellType::FLOAT:  return get_fun<T, float, Args...>(a_type);
    case CellType::INT:    return get_fun<T, int, Args...>(a_type);
    }
    abort();
}

template <typename T>
auto get_fun(CellType a_type, CellType b_type, CellType c_type) {
    switch(c_type) {
    case CellType::DOUBLE: return get_fun<T, double>(a_type, b_type);
    case CellType::FLOAT:  return get_fun<T, float>(a_type, b_type);
    case CellType::INT:    return get_fun<T, int>(a_type, b_type);
    }
    abort();
}

//-----------------------------------------------------------------------------

struct CellwiseAdd3 {
    struct Self;
    using fun_type = void (*)(const TypedCells &x, const TypedCells &y, const TypedCells &z, size_t cnt, Self &self);
    template <typename A, typename B, typename C>
    static fun_type get_fun();
    struct Self {
        fun_type my_fun;
        Self();
    };
    Self self;
    void call(const TypedCells &x, const TypedCells &y, const TypedCells &z, size_t cnt) {
        self.my_fun(x, y, z, cnt, self);
    }
};

template <typename A, typename B, typename C>
void cellwise_add(const TypedCells &x, const TypedCells &y, const TypedCells &z, size_t cnt, CellwiseAdd3::Self &self) {
    if (!x.check_type<A>() || !y.check_type<B>() || !z.check_type<C>()) {
        auto new_fun = get_fun<CellwiseAdd3>(x.type, y.type, z.type);
        self.my_fun = new_fun;
        return new_fun(x, y, z, cnt, self);
    }
    auto a = x.unsafe_typify<A>();
    auto b = y.unsafe_typify<B>();
    auto c = z.unsafe_typify<C>();
    auto dst = unconstify(c);
    for (size_t i = 0; i < cnt; ++i) {
        dst[i] = a[i] + b[i];
    }
};

template <typename A, typename B, typename C>
CellwiseAdd3::fun_type CellwiseAdd3::get_fun() {
    return cellwise_add<A, B, C>;
}

CellwiseAdd3::Self::Self()
    : my_fun(cellwise_add<double, double, double>)
{
}

//-----------------------------------------------------------------------------

struct DotProduct3 {
    struct Self;
    using fun_type = double (*)(const TypedCells &x, const TypedCells &y, size_t cnt, Self &self);
    template <typename A, typename B>
    static fun_type get_fun();
    struct Self {
        fun_type my_fun;
        Self();
    };
    Self self;
    double call(const TypedCells &x, const TypedCells &y, size_t cnt) {
        return self.my_fun(x, y, cnt, self);
    }
};

template <typename A, typename B>
double dot_product(const TypedCells &x, const TypedCells &y, size_t cnt, DotProduct3::Self &self) {
    if (!x.check_type<A>() || !y.check_type<B>()) {
        auto new_fun = get_fun<DotProduct3>(x.type, y.type);
        self.my_fun = new_fun;
        return new_fun(x, y, cnt, self);
    }
    auto a = x.unsafe_typify<A>();
    auto b = y.unsafe_typify<B>();
    double result = 0.0;
    for (size_t i = 0; i < cnt; ++i) {
        result += (a[i] * b[i]);
    }
    return result;
}

template <typename A, typename B>
DotProduct3::fun_type DotProduct3::get_fun() {
    return dot_product<A, B>;
}

DotProduct3::Self::Self()
    : my_fun(dot_product<double, double>)
{
}

//-----------------------------------------------------------------------------

struct Sum3 {
    struct Self;
    using fun_type = double (*)(const TypedCells &x, Self &self);
    template <typename A>
    static fun_type get_fun();
    struct Self {
        fun_type my_fun;
        Self();
    };
    Self self;
    double call(const TypedCells &x) {
        return self.my_fun(x, self);
    }
};

template <typename A>
double sum(const TypedCells &x, Sum3::Self &self) {
    if (!x.check_type<A>()) {
        auto new_fun = get_fun<Sum3>(x.type);
        self.my_fun = new_fun;
        return new_fun(x, self);
    }
    auto a = x.unsafe_typify<A>();
    double result = 0.0;
    for (const auto &value: a) {
        result += value;
    }
    return result;
}

template <typename A>
Sum3::fun_type Sum3::get_fun() {
    return sum<A>;
}

Sum3::Self::Self()
    : my_fun(sum<double>)
{
}

//-----------------------------------------------------------------------------

TEST("require that self-updating cached function pointer 'a op b -> c' works") {
    std::vector<int>      a({1,2,3});
    std::vector<float>    b({1.5,2.5,3.5});
    std::vector<double>   c(3, 0.0);
    TypedCells            a_cells(a);
    TypedCells            b_cells(b);
    TypedCells            c_cells(c);

    CellwiseAdd3 op;
    EXPECT_EQUAL(op.self.my_fun, (&cellwise_add<double,double,double>));
    op.call(a_cells, b_cells, c_cells, 3);
    EXPECT_EQUAL(op.self.my_fun, (&cellwise_add<int,float,double>));
    EXPECT_NOT_EQUAL(op.self.my_fun, (&cellwise_add<double,double,double>));

    EXPECT_EQUAL(c[0], 2.5);
    EXPECT_EQUAL(c[1], 4.5);
    EXPECT_EQUAL(c[2], 6.5);
}

TEST("require that self-updating cached function pointer 'dot product' with return value works") {
    std::vector<int>      a({1,2,3});
    std::vector<float>    b({1.5,2.5,3.5});
    TypedCells            a_cells(a);
    TypedCells            b_cells(b);
    double                expect = 1.5 + (2 * 2.5) + (3 * 3.5);

    DotProduct3 op;
    EXPECT_EQUAL(op.self.my_fun, (&dot_product<double,double>));
    EXPECT_EQUAL(expect, op.call(a_cells, b_cells, 3));
    EXPECT_EQUAL(op.self.my_fun, (&dot_product<int,float>));
    EXPECT_NOT_EQUAL(op.self.my_fun, (&dot_product<double,double>));
}

TEST("require that self-updating cached function pointer 'sum' with return value works") {
    std::vector<int>      a({1,2,3});
    TypedCells            a_cells(a);
    double                expect = (1 + 2 + 3);

    Sum3 op;
    EXPECT_EQUAL(op.self.my_fun, (&sum<double>));
    EXPECT_EQUAL(expect, op.call(a_cells));
    EXPECT_EQUAL(op.self.my_fun, (&sum<int>));
    EXPECT_NOT_EQUAL(op.self.my_fun, (&sum<double>));
}

TEST_MAIN() { TEST_RUN_ALL(); }
