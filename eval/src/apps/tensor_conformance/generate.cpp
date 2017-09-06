// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generate.h"

using TensorSpec = vespalib::eval::TensorSpec;

TensorSpec spec(double value) { return TensorSpec("double").add({}, value); }

void
Generator::generate(TestBuilder &dst)
{
    // smoke tests with expected result
    dst.add("a+a", {{"a", spec(2.0)}}, spec(4.0));
    dst.add("a*b", {{"a", spec(2.0)}, {"b", spec(3.0)}}, spec(6.0));
    dst.add("(a+b)*(a-b)", {{"a", spec(5.0)}, {"b", spec(2.0)}}, spec(21.0));
    // smoke test without expected result
    dst.add("(a-b)/(a+b)", {{"a", spec(5.0)}, {"b", spec(2.0)}});
}
