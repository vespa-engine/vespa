// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "log-target.h"
#include "llparser.h"
#include "internal.h"
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <sys/time.h>
#include <cassert>
#include <vespa/defaults.h>

namespace ns_log {

static const char *defcomponent = "logger";
static const char *defservice = "-";

LLParser::LLParser()
    : _defHostname(vespa::Defaults::vespaHostname()),
      _defService(defservice),
      _defComponent(defcomponent),
      _defLevel(Logger::info),
      _target(Logger::getCurrentTarget()),
      _rejectFilter(RejectFilter::createDefaultFilter())
{
    assert(_target != nullptr);
    const char *envServ = getenv("VESPA_SERVICE_NAME");
    if (envServ != NULL) {
        _defService = envServ;
    }
    snprintf(_defPid, 10, "%d", (int)getpid());
}

LLParser::~LLParser() = default;

const char LLParser::_hexdigit[17] = "0123456789abcdef";

void
LLParser::sendMessage(const char *totalMessage)
{
    _target->write(totalMessage, strlen(totalMessage));
}

static inline bool validLevel(Logger::LogLevel level)
{
    return (level >= 0 && level < Logger::NUM_LOGLEVELS);
}

static bool isValidPid(const char *field)
{
    char *eol;
    long pidnum = strtol(field, &eol, 10);
    if (pidnum > 0 && pidnum < 18*1000*1000) {
        char endbyte = *eol;
        if (endbyte == '\0' || endbyte == '\t' || endbyte == '/') {
            return true;
        }
        return false;
    }
    // too big to be a valid pid, maybe a timestamp?
    if (pidnum >= 18*1000*1000)
        return false;

    // stupid java logging...
    if (field[0] == '-' && field[1] == '\0') {
        return true;
    }
    if (field[0] == '-' && field[1] == '/') {
        if (field[2] == '-' && field[3] == '\0') {
            return true;
        }
        // thread id
        pidnum = strtol(field+2, &eol, 10);
        if (eol > field+2) {
            char endbyte = *eol;
            if (endbyte == '\0' || endbyte =='\t') {
                return true;
            }
        }
    }
    return false;
}

void
LLParser::doInput(char *line)
{
    double logTime = 0.0;
    bool timefield = false;
    int pidfield = 0;
    char *eod = NULL;

    char *first = line;
    char *tab = strchr(first, '\t');	// time?
    char empty[1] = "";

    if (tab) {
        *tab = '\0';
        logTime = strtod(first, &eod);
        if (eod == tab && logTime > 900*1000*1000) {
            timefield = true;
        } else if (isValidPid(first)) {
            pidfield = 1;
        }
        char *second = tab+1;
        tab = strchr(second, '\t');	// host?
        if (tab) {
            *tab = '\0';
	    if (pidfield == 0) {
                if (isValidPid(second)) {
		    pidfield = 2;
		}
	    }
	    char *third  = tab+1;
	    tab = strchr(third, '\t');	// pid?
	    if (tab) {
                *tab = '\0';
		if (pidfield == 0) {
		    if (isValidPid(third)) {
			pidfield = 3;
		    }
		}
		char *fourth = tab+1;
	        tab = strchr(fourth, '\t');	// service ?
		if (tab) {
                    *tab = '\0';
		    char *fifth = tab+1;
		    tab = strchr(fifth, '\t');	// component ?
		    if (tab) {
                        *tab = '\0';
			char *sixth = tab+1;
			tab = strchr(sixth, '\t');	// level?
			if (tab && timefield) {
			    *tab = '\0';
			    char *seventh = tab+1;	// message
			    Logger::LogLevel l = Logger::parseLevel(sixth);
			    if (validLevel(l)) {
				makeMessage(first, second, third, fourth,
                                        fifth, l, seventh);
				return;
			    }
			    // pretend 6 fields
                            *tab = '\t';
			}
			Logger::LogLevel l = Logger::parseLevel(fifth);
			if (validLevel(l)) {
			    // missing one field - which one?
			    if (timefield && pidfield == 2) {
				// missing host
				makeMessage(first, empty, second, third,
                                        fourth, l, sixth);
				return;
			    }
			    if (timefield && pidfield == 3) {
				// missing service
				makeMessage(first, second, third, empty,
                                        fourth, l, sixth);
				return;
			    }
                            if (!timefield && pidfield == 2) {
                                // missing time
                                makeMessage(empty, first, second, third,
                                        fourth, l, sixth);
                                return;
                            }
			    if (timefield && pidfield == 0) {
				// missing pid
                                makeMessage(first, second, empty, third,
                                        fourth, l, sixth);
                                return;
			    }
                            // fprintf(stderr, "bad 6-field\n");
			    // no idea what is going on
			}
			// pretend 5 fields
		        tab = sixth-1;
                        *tab = '\t';
		    }
		    Logger::LogLevel l = Logger::parseLevel(fourth);
		    if (validLevel(l)) {
			// missing two fields
                        if (!timefield && pidfield == 0) {
                            // missing time and pid
                            makeMessage(empty, first, empty, second,
                                        third, l, fifth);
                            return;
                        }
                        if (!timefield && pidfield == 1) {
                            // missing time and host
                            makeMessage(empty, empty, first, second,
                                        third, l, fifth);
                            return;
                        }
                        if (!timefield && pidfield == 2) {
                            // missing time and service
                            makeMessage(empty, first, second, empty,
                                        third, l, fifth);
                            return;
                        }
			if (timefield && pidfield == 2) {
                            // missing host and service
                            makeMessage(first, empty, second, empty,
                                        third, l, fifth);
                            return;
			}
			if (timefield && pidfield == 3) {
			    // missing service and component
			    makeMessage(first, second, third, empty,
				        empty, l, fifth);
			    return;
			}
			if (timefield && pidfield == 0) {
			    // missing pid and (hostname or service)
			    if (_defService == second) {
				// it's the service, assume missing hostname
				makeMessage(first, empty, empty, second,
					    third, l, fifth);
				return;
			    }
			    makeMessage(first, second, empty, empty,
				        third, l, fifth);
			    return;
			}
                        // fprintf(stderr, "bad 5-field\n");
                        // no idea what is going on
		    }
		    // pretend 4 fields
		    tab = fifth-1;
                    *tab = '\t';
		}
		Logger::LogLevel l = Logger::parseLevel(third);
		if (validLevel(l)) {
		    // three fields + message
		    if (timefield && pidfield == 2) {
			// missing host, service, component
			makeMessage(first, empty, second, empty,
				    empty, l, fourth);
			return;
		    }
		    if (timefield && pidfield == 0) {
			// missing host, pid, service
			makeMessage(first, empty, empty, empty,
				    second, l, fourth);
			return;
		    }
                    if (!timefield && pidfield == 1) {
                        // missing time, host, service
                        makeMessage(empty, empty, first, empty,
                                    second, l, fourth);
                        return;
                    }
                    if (!timefield && pidfield == 0) {
                        // missing time, pid, and (host or service)
			if (_defService == first) {
			    // it's the service, assume missing hostname
			    makeMessage(empty, empty, empty, first,
					second, l, fourth);
			    return;
			}
			// missing service
			makeMessage(empty, first, empty, empty,
				    second, l, fourth);
			return;
                    }
                    // fprintf(stderr, "bad 4-field\n");
                    // no idea what is going on

		}
		// pretend 3 fields
		tab = fourth-1;
                *tab = '\t';
	    }
	    Logger::LogLevel l = Logger::parseLevel(second);
	    if (validLevel(l)) {
		if (timefield) {
		    // time, level, message
		    makeMessage(first, empty, empty, empty,
				empty, l, third);
		    return;
		}
		if (pidfield) {
		    // pid, level, message
		    makeMessage(empty, empty, first, empty,
				empty, l, third);
		    return;
		}
		// component, level, message
		makeMessage(empty, empty, empty, empty,
			    first, l, third);
		return;
	    }
            // pretend 2 fields
	    tab = third-1;
            *tab = '\t';
	}
	Logger::LogLevel l = Logger::parseLevel(first);

	if (validLevel(l)) {
            makeMessage(empty, empty, empty, empty,
                        empty, l, second);
            return;
	}
        // pretend 1 field
	tab = second-1;
        *tab = '\t';
    }
    makeMessage(empty, empty, empty, empty,
                empty, _defLevel, line);
}


static char escaped[16000];
static char totalMessage[17000];

void
LLParser::makeMessage(const char *tmf, const char *hsf, const char *pdf,
                      const char *svf, const char *cmf, Logger::LogLevel level,
                      char *src)
{
    char tmbuffer[24];
    if (tmf[0] == '\0') {
        struct timeval tv;
        gettimeofday(&tv, NULL);
        snprintf(tmbuffer, 24, "%u.%06u",
                 static_cast<unsigned int>(tv.tv_sec),
                 static_cast<unsigned int>(tv.tv_usec));
        tmf = tmbuffer;
    }

    if (hsf[0] == '\0') hsf = _defHostname.c_str();

    if (pdf[0] == '\0') pdf = _defPid;

    if (svf[0] == '\0') svf = _defService.c_str();

    if (cmf[0] == '\0') cmf = _defComponent.c_str();

    char *dst = escaped;
    unsigned char c;

    int len = strlen(src);
    if (len > 3999) {
	src[3997] = '.';
	src[3998] = '.';
	src[3999] = '.';
	src[4000] = '\0';
    }
    do {
        c = static_cast<unsigned char>(*src++);
        if ((c == '\\' && src[0] == 't')
            || (c >= 32 && c < '\\')
            || (c > '\\' && c < 128)
            || c == 0)
	{
            *dst++ = static_cast<char>(c);
        } else {
            *dst++ = '\\';
            if (c == '\\') {
                *dst++ = '\\';
            } else if (c == '\r') {
                *dst++ = 'r';
            } else if (c == '\n') {
                *dst++ = 'n';
            } else if (c == '\t') {
                *dst++ = 't';
            } else {
                *dst++ = 'x';
                *dst++ = _hexdigit[c >> 4];
                *dst++ = _hexdigit[c & 0xf];
            }
        }
    } while (c);

    snprintf(totalMessage, sizeof totalMessage,
             "%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
             tmf, hsf, pdf, svf, cmf,
             Logger::logLevelNames[level], escaped);

    if (_rejectFilter.shouldReject(level, escaped))
        return;
    sendMessage(totalMessage);
}


void
LLParser::setPid(int p)
{
    snprintf(_defPid, 10, "%d", p);
}

} // namespace
