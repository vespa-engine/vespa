// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <string>

struct DocumentTestUtils
{
    static std::string srcDir()
    {
        static const std::string srcDir = getenv("SOURCE_DIRECTORY") ? std::string(getenv("SOURCE_DIRECTORY")) + "/" : "./";
        return srcDir;
    }
};