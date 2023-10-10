#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

class=$1
guard=`echo $class | tr 'a-z' 'A-Z'`

cat <<EOF
// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace search {
namespace fef {

class $class
{
private:
    $class(const $class &);
    $class &operator=(const $class &);
public:
    $class();
    virtual ~$class();
};

} // namespace fef
} // namespace search

EOF
