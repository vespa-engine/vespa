// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "typed_data_layout.h"

namespace vespalib::tdl {

// template class Domain<>; // empty domain not allowed
// template class Domain<int,int>; // duplicate types not allowed
// template class Layout<int,detail::EmptyBase>; // must use domain
// template class Data<int,detail::EmptyBase>; // must use domain

namespace {

using MyDomain = Domain<int, double, bool>;

static_assert(MyDomain::num_types == 3);
static_assert(std::same_as<MyDomain::type_at<0>, int>);
static_assert(std::same_as<MyDomain::type_at<1>, double>);
static_assert(std::same_as<MyDomain::type_at<2>, bool>);
// static_assert(std::same_as<MyDomain::type_at<3>, float>); // index too large

static_assert(MyDomain::type_id<int> == 0);
static_assert(MyDomain::type_id<double> == 1);
static_assert(MyDomain::type_id<bool> == 2);
// static_assert(MyDomain::type_id<float> == 3); // type not found

static_assert(Domain<char>::max_align == 1);
static_assert(Domain<char, uint32_t>::max_align == 4);
static_assert(Domain<char, uint64_t, uint32_t>::max_align == 8);

struct alignas(32) BigAlign {};
static_assert(std::same_as<decltype(detail::full_align<MyDomain, detail::EmptyBase>()), std::align_val_t>);
static_assert(detail::full_align<Domain<char>, detail::EmptyBase>() == std::align_val_t(1));
static_assert(detail::full_align<Domain<char, int, double>, detail::EmptyBase>() == std::align_val_t(8));
static_assert(detail::full_align<Domain<char, BigAlign, double>, detail::EmptyBase>() == std::align_val_t(32));
static_assert(detail::full_align<Domain<char, int, double>, BigAlign>() == std::align_val_t(32));

} // namespace
} // namespace vespalib::tdl
