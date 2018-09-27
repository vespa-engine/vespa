// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "perform.h"
#include "cmdbuf.h"
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP("");

namespace logdemon {

namespace {

bool
isPrefix(const char *prefix, const char *line)
{
    while (*prefix) {
        if (*prefix++ != *line++) return false;
    }
    return true;
}

}

Performer::~Performer() = default;
ExternalPerformer::~ExternalPerformer() = default;

void
ExternalPerformer::listStates(const char *service, const char *component)
{
    Service *svc = _services.getService(service);
    Component *cmp = svc->getComponent(component);

    using ns_log::Logger;

    char buf[1024];
    int pos = snprintf(buf, 1024, "state %s %s ", service, component);
    for (int i = 0; pos < 1000 && i < Logger::NUM_LOGLEVELS; i++) {
        LogLevel level = static_cast<LogLevel>(i);
        const char *levstate = "off";
        if (cmp->shouldLogAtAll(level)) {
            if (cmp->shouldForward(level)) {
                levstate = "forward";
            } else {
                levstate = "store";
            }
        }
        pos += snprintf(buf+pos, 1024-pos, "%s=%s,", Logger::logLevelNames[i], levstate);
    }
    if (pos < 1000) {
        buf[pos-1]='\n';
        _forwarder.forwardText(buf, pos);
    } else {
        LOG(warning, "buffer to small to list states[%s, %s]", service, component);
    }
}

void
ExternalPerformer::doCmd(char *line)
{
    if (isPrefix("list services", line)) {
        for (const auto & entry : _services._services) {
            char buf[1024];
            snprintf(buf, 1024, "service %s\n", entry.first.c_str());
            _forwarder.forwardText(buf, strlen(buf));
        }
        return;
    }
    if (isPrefix("list components ", line)) {
        const char *servstr = line+5+11;
        Service *svc = _services.getService(servstr);
        for (const auto & entry : svc->_components) {
            const char * key = entry.first.c_str();
            char buf[1024];
            snprintf(buf, 1024, "component %s %s\n", servstr, key);
            int len = strlen(buf);
            if (len < 1000) {
                _forwarder.forwardText(buf, len);
            } else {
                LOG(warning, "buffer too small to list component %s %s", servstr, key);
            }
        }
        return;
    }
    if (isPrefix("list states ", line)) {
        char *servstr = line+5+7;
        char *compstr = strchr(servstr, ' ');
        if (compstr == nullptr) {
            Service *svc = _services.getService(servstr);
            for (const auto & entry : svc->_components) {
                listStates(servstr, entry.first.c_str());
            }
            return;
        }
        *compstr++ = '\0';
        listStates(servstr, compstr);
        return;
    }
    if (isPrefix("setallstates", line)) {
        char *levmods = strchr(line, ' ');
        if (levmods == nullptr) {
            LOG(error, "bad command: %s", line);
        } else {
            vespalib::string orig(line);
            *levmods++ = '\0';
            doSetAllStates(levmods, orig.c_str());
        }
        return;
    }
    if (isPrefix("setstate ", line)) {
        char *servstr = line + 9;
        char *compstr = strchr(servstr, ' ');
        if (compstr == nullptr) {
            LOG(error, "bad command: %s", line);
            return;
        }
        *compstr++ = '\0';
        char *levmods = strchr(compstr, ' ');
        if (levmods == nullptr) {
            LOG(error, "bad command: %s %s", line, compstr);
            return;
        }
        *levmods++ = '\0';

        Service *svc = _services.getService(servstr);
        Component *cmp = svc->getComponent(compstr);
        if (doSetState(levmods, cmp, line) == nullptr) return;

        // maybe ???
        listStates(servstr, compstr);
        return;
    }
    if (isPrefix("setdefaultstate ", line)) {
        char *levmods = strchr(line, ' ');
        *levmods++ = '\0';
        while (levmods) {
            char *newval = strchr(levmods, '=');
            if (!newval) {
                LOG(error, "bad command %s: expected level=value, got %s",
                    line, levmods);
                return;
            }
            *newval++ = '\0';

            LogLevel level = _levelparser.parseLevel(levmods);
            char *nextlev = strchr(newval, ',');
            if (nextlev) *nextlev++ = '\0';
            if (strcmp(newval, "forward") == 0) {
                Component::defaultDoForward(level);
            } else if (strcmp(newval, "noforward") == 0) {
                Component::defaultDontForward(level);
            } else {
                LOG(error, "bad command %s %s=%s: want forward/noforward",
                    line, levmods, newval);
                return;
            }
            levmods = nextlev;
        }
        return;
    }
    LOG(error, "unknown command: %s", line);
}

void
ExternalPerformer::doSetAllStates(char *levmods, const char *origline) {
    while (levmods) {
        char *newval = strchr(levmods, '=');
        if (!newval) {
            LOG(error, "bad command %s : expected level=value, got %s", origline, levmods);
            return;
        }
        *newval++ = '\0';

        LogLevel level = _levelparser.parseLevel(levmods);
        char *nextlev = strchr(newval, ',');
        if (nextlev) *nextlev++ = '\0';
        for (const auto & serviceEntry : _services._services) {
            Service *svc = _services.getService(serviceEntry.first);
            for (const auto & entry : svc->_components) {
                Component *cmp = svc->getComponent(entry.first);
                assert(cmp != 0);

                if (strcmp(newval, "forward") == 0) {
                    cmp->doForward(level);
                    cmp->doLogAtAll(level);
                } else if (strcmp(newval, "noforward") == 0) {
                    cmp->dontForward(level);
                } else if (strcmp(newval, "store") == 0) {
                    cmp->dontForward(level);
                    cmp->doLogAtAll(level);
                } else if (strcmp(newval, "off") == 0) {
                    cmp->dontForward(level);
                    cmp->dontLogAtAll(level);
                } else {
                    LOG(error, "bad command %s: want forward/store/off, got %s", origline, newval);
                    return;
                }
            }
        }

        levmods = nextlev;
    }
}

char *
ExternalPerformer::doSetState(char *levmods, Component *cmp, char * line) {
    while (levmods) {
        char *newval = strchr(levmods, '=');
        if (!newval) {
            LOG(error, "bad command %s : expected level=value, got %s", line, levmods);
            return nullptr;
        }
        *newval++ = '\0';

        LogLevel level = _levelparser.parseLevel(levmods);
        char *nextlev = strchr(newval, ',');
        if (nextlev) *nextlev++ = '\0';
        if (strcmp(newval, "forward") == 0) {
            cmp->doForward(level);
            cmp->doLogAtAll(level);
        } else if (strcmp(newval, "noforward") == 0) {
            cmp->dontForward(level);
        } else if (strcmp(newval, "store") == 0) {
            cmp->dontForward(level);
            cmp->doLogAtAll(level);
        } else if (strcmp(newval, "off") == 0) {
            cmp->dontForward(level);
            cmp->dontLogAtAll(level);
        } else {
            LOG(error, "bad command %s %s=%s: want forward/store/off", line, levmods, newval);
            return nullptr;
        }
        levmods = nextlev;
    }
    return levmods;
}

void
InternalPerformer::doCmd(char *line)
{
    if (isPrefix("setstate ", line)){
        char *servstr = line + 9;
        char *compstr = strchr(servstr, ' ');
        if (compstr == nullptr) {
            LOG(error, "bad internal command: %s", line);
            return;
        }
        *compstr++ = '\0';
        char *levmods = strchr(compstr, ' ');
        if (levmods == nullptr) {
            LOG(error, "bad internal command: %s %s", line, compstr);
            return;
        }
        *levmods++ = '\0';

        // ignore services with slash in the name, invalid
        if (strchr(servstr, '/') != nullptr)
            return;

        Service *svc = _services.getService(servstr);
        Component *cmp = svc->getComponent(compstr);

        while (levmods) {
            char *newval = strchr(levmods, '=');
            if (!newval) {
                LOG(error, "bad internal %s %s: expected level=value, got %s", line, compstr, levmods);
                return;
            }
            *newval++ = '\0';

            LogLevel level = _levelparser.parseLevel(levmods);
            char *nextlev = strchr(newval, ',');
            if (nextlev) *nextlev++ = '\0';
            if (strcmp(newval, "forward") == 0) {
                cmp->doForward(level);
            } else if (strcmp(newval, "store") == 0) {
                cmp->dontForward(level);
            } else if (strcmp(newval, "off") == 0) {
                cmp->dontForward(level);
            } else {
                LOG(error, "bad internal %s %s %s=%s: want forward/store/off", line, compstr, levmods, newval);
                return;
            }
            levmods = nextlev;
        }
        return;
    }
    LOG(error, "unknown command: %s", line);
}

} // namespace
