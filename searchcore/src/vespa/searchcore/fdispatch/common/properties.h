// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1999-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

class FastS_IProperties
{
public:
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FastS_IProperties() { }

    virtual bool        IsSet    (const char *key)                         = 0;
    virtual bool        BoolVal  (const char *key)                         = 0;
    virtual const char *StrVal   (const char *key, const char *def = NULL) = 0;
    virtual int         IntVal   (const char *key, int def = -1)           = 0;
    virtual double      DoubleVal(const char *key, double def = 0.0)       = 0;
};

