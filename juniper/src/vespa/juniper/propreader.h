// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "IJuniperProperties.h"
#include "stringmap.h"

/** Simple property reader using same format as fsearchrc.
 *  Implemented for standalone testing of Juniper.
 */
class PropReader : public IJuniperProperties
{
public:
    PropReader(const char* filename);
    virtual const char* GetProperty(const char* name, const char* def = NULL);
    void UpdateProperty(const char* name, const char* value);
    virtual ~PropReader() {}
protected:
    void Process(const char* filename);
private:
    Fast_StringMap _keymap;
};

