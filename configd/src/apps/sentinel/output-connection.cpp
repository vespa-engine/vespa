// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <unistd.h>
#include <stdarg.h>
#include <cstdio>
#include <cstring>

#include <vespa/log/log.h>
LOG_SETUP(".sentinel.output-connection");
#include <vespa/log/llparser.h>

#include "output-connection.h"
#include "line-splitter.h"

namespace config {
namespace sentinel {

OutputConnection::OutputConnection(int f, ns_log::LLParser *p)
    : _fd(f),
      _lines(f),
      _parser(p)
{
}

bool
OutputConnection::isFinished() const
{
    return _lines.eof();
}

void
OutputConnection::handleOutput()
{
    while (1) {
	char *line =  _lines.getLine();
	if (!line) {
	    return;
	}
	LOG(spam, "Got Output from connection: '%s'", line);
	_parser->doInput(line);
    }
}

OutputConnection::~OutputConnection()
{
    close(_fd);
    delete _parser;
}

} // end namespace config::sentinel
} // end namespace config
