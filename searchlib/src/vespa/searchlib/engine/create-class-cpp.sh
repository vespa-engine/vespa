#!/bin/sh
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

class=$1
guard=`echo $class | tr 'a-z' 'A-Z'`
name=`echo $class | tr 'A-Z' 'a-z'`

cat <<EOF
// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "$name.h"

namespace search::engine {

$class::$class()
{
}

$class::~$class()
{
}

} // namespace search::engine
EOF
