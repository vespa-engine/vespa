// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <cstdio>

#include "log.h"
LOG_SETUP_INDIRECT(".log.control", "$Id$");
#undef LOG
#define LOG LOG_INDIRECT

#include "component.h"
#include "control-file.h"
#include "internal.h"

namespace ns_log {

bool
Component::matches(const char *pattern)
{
    // Return true if pattern matches the name of this component,
    // false otherwise.
    int pLen = strlen(pattern);
    bool matched;

    if (strcmp(pattern, "default") == 0) {
        return true;
    }

    if (strcmp(pattern, ".") == 0) {
        return matches("default.");
    }

    if (pattern[pLen - 1] == '.') {
        LOG(spam, "Component::matches -- exact match of '%s' vs name '%s'",
            pattern, _name);
        matched = (strncmp(pattern, _name, pLen - 1) == 0)
                  && _name[pLen - 1] == ':';
    } else {
        LOG(spam, "Component::matches -- prefix match of '%s' vs name '%s'",
            pattern, _name);
        matched = strncmp(pattern, _name, pLen) == 0
                  && (_name[pLen] == '.' || _name[pLen] == ':');
    }
    LOG(spam, "Component::matches: Pattern '%s' %s match name '%s'",
        pattern, matched ? "did" : "did not", _name);
    return matched;
}

void
Component::modifyLevels(const char *levels)
{
    // levels is a comma-separated list of level={on|off} pairs.

    // the levels string can always be converted to a
    // AND bitmask -- for all levels to be removed
    // and an OR bitmask -- for all levels to be added
    std::string levels_copy(levels);
    char *s = &levels_copy[0];

    LOG(spam, "Will modify levels for '%.*s' according to \"%s\"",
        (int)strcspn(_name, " :\n"), _name, levels);

    while (s && *s) {
        char *eq = strchr(s, '=');
        if (!eq) {
            throwInvalid("Missing \"=\" in levels string at \"%s\"", s);
        }
        *eq = 0;
        Logger::LogLevel level = Logger::parseLevel(s);
        if (level == Logger::NUM_LOGLEVELS) {
            if (strcmp(s, "all") != 0) {
                throwInvalid("Level name at \"%s\" is not valid", s);
            }
        }

        *eq = '=';
        char *mod = eq + 1;
        unsigned int newValue;

        if (strcmp(mod, "on") == 0 || strncmp(mod, "on,", 3) == 0) {
            s = mod + 2;
            newValue = CHARS_TO_UINT(' ', ' ', 'O', 'N');
        } else if (strcmp(mod, "off") == 0 || strncmp(mod, "off,", 4) == 0) {
            s = mod + 3;
            newValue = CHARS_TO_UINT(' ', 'O', 'F', 'F');
        } else {
            throwInvalid("Invalid modification string at \"%s\", expected "
                         "\"on\" or \"off\"", mod);
        }
        if (*s == ',') {
            ++s;
        }

        if (level != Logger::NUM_LOGLEVELS) {
            _intLevels[level] = newValue;
        } else {
            for (int n = 0; n != Logger::NUM_LOGLEVELS; ++n) {
                _intLevels[n] = newValue;
            }
        }
    }
    display();
}

void
Component::display()
{
    int nlen = strcspn(_name, ":\n ");
    printf("%-30.*s %.*s\n", nlen, _name,
           (int)(sizeof(unsigned int) * Logger::NUM_LOGLEVELS), _charLevels);
}

Component::Component(char *s)
    : _name(s),
      _charLevels(ControlFile::alignLevels(strchr(s, ':') + 2)),
      _intLevels(reinterpret_cast<unsigned int *>(_charLevels))
{
    if (_charLevels == reinterpret_cast<char *>(4)) {
        throwInvalid("Invalid component instantiated");
    }

}

} // end namesmace ns_log
