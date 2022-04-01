// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Class definitions for FastOS_UNIX_Application.
 *
 * @author  Div, Oivind H. Danielsen
 */

#pragma once

#include "app.h"
#include "types.h"
#include <memory>

/**
 * This is the generic UNIX implementation of @ref FastOS_ApplicationInterface
 */
class FastOS_UNIX_Application : public FastOS_ApplicationInterface
{
protected:
    bool PreThreadInit () override;
public:
    FastOS_UNIX_Application ();
    FastOS_UNIX_Application(const FastOS_UNIX_Application&) = delete;
    FastOS_UNIX_Application& operator=(const FastOS_UNIX_Application&) = delete;
    virtual ~FastOS_UNIX_Application();
    bool Init () override;
    void Cleanup () override;
};
