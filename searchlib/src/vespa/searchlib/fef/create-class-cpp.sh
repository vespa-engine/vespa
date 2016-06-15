#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

class=$1
guard=`echo $class | tr 'a-z' 'A-Z'`
name=`echo $class | tr 'A-Z' 'a-z'`

cat <<EOF
// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP(".fef.$name");
#include <vespa/fastos/fastos.h>
#include "$name.h"

namespace search {
namespace fef {

$class::$class()
{
}

$class::~$class()
{
}

} // namespace fef
} // namespace search
EOF
