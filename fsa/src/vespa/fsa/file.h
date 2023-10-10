// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2008/05/30
 * @version $Id$
 * @file    file.h
 * @brief   Currently just %FileAccessMethod
 */

#pragma once

namespace fsa {

// {{{ FileAccessMethod

/**
 * @brief File access method enum.
 */
enum FileAccessMethod {
  FILE_ACCESS_UNDEF,
  FILE_ACCESS_READ,
  FILE_ACCESS_MMAP,
  FILE_ACCESS_MMAP_WITH_MLOCK
};

// }}}

} // namespace fsa

