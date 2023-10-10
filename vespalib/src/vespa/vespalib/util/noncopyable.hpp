// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib {

/**
 * A convenience class that ensures classes inheriting this cannot be
 * copied.
 */
namespace noncopyable_
{
    class noncopyable
    {
    protected:
        noncopyable() {}
        ~noncopyable() {}
    private:
        noncopyable(const noncopyable &);
        const noncopyable & operator=(const noncopyable &);
  };
}

using noncopyable = noncopyable_::noncopyable;

} // namespace vespalib

