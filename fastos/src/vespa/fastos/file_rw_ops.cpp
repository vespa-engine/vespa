// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "file_rw_ops.h"
#include <unistd.h>

namespace fastos {

File_RW_Ops::ReadFunc File_RW_Ops::_read = ::read;
File_RW_Ops::WriteFunc File_RW_Ops::_write = ::write;
File_RW_Ops::PreadFunc File_RW_Ops::_pread = ::pread;
File_RW_Ops::PwriteFunc File_RW_Ops::_pwrite = ::pwrite;

}
