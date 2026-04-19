// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "cursor.h"
#include "inject.h"
#include "inserter.h"
#include "inspector.h"
#include "type.h"

namespace vespalib {

class Slime;
struct Memory;

namespace slime {

class Type;
class Symbol;

namespace convenience {

using ::vespalib::Slime;

using ::vespalib::Memory;
using ::vespalib::slime::Cursor;
using ::vespalib::slime::Inspector;
using ::vespalib::slime::Symbol;
using ::vespalib::slime::Type;

using ::vespalib::slime::ArrayInserter;
using ::vespalib::slime::inject;
using ::vespalib::slime::Inserter;
using ::vespalib::slime::ObjectInserter;
using ::vespalib::slime::ObjectSymbolInserter;
using ::vespalib::slime::SlimeInserter;

} // namespace convenience
} // namespace slime
} // namespace vespalib
