// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "typed_data_layout.h"

namespace vespalib::tdl {

// template class Domain<>; // empty domain not allowed
// template class Domain<int,int>; // duplicate types not allowed
// template class Layout<int>; // must use domain
// template class Data<int>; // must use domain

namespace {

using MyDomain = Domain<int,double,bool>;

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
static_assert(Domain<char,uint32_t>::max_align == 4);
static_assert(Domain<char,uint64_t,uint32_t>::max_align == 8);

} // <unnamed>
} // vespalib::tdl
