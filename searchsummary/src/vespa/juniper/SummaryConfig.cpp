// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#define _NEED_SUMMARY_CONFIG_IMPL 1
#include "SummaryConfig.h"
#include <string>
#include <cstring>

SummaryConfig* CreateSummaryConfig(const char* highlight_on,
				   const char* highlight_off,
				   const char* dots,
				   const char* separators,
				   const unsigned char* connectors,
				   const ConfigFlag escape_markup,
				   const ConfigFlag preserve_white_space)
{
    return new SummaryConfig(highlight_on, highlight_off, dots, separators,
                             connectors, escape_markup, preserve_white_space);
}


void DeleteSummaryConfig(SummaryConfig*& sumconf)
{
    delete sumconf;
    sumconf = NULL;
}


inline char hexchar(const char* s)
{
    const char* str = s;
    unsigned char c = 0;
    for (int i = 0; i < 2; i++)
    {
        if (*str <= 'F')
            c |= (*str - '0');
        else
            c |= (*str - 'a' + 10);
        c = c << ((1 - i)*4);
        str++;
    }
    return (char)c;
}


SummaryConfig::SummaryConfig(const char* hi_on, const char* hi_off,
                             const char* usedots, const char* separators,
                             const unsigned char* connectors,
                             ConfigFlag esc_markup,
                             ConfigFlag preserve_white_space_)
    : _highlight_on(""),
      _highlight_off(""),
      _dots(""),
      _separator(),
      _connector(),
      _escape_markup(esc_markup),
      _preserve_white_space(preserve_white_space_)
{
    init(_highlight_on, hi_on);
    init(_highlight_off, hi_off);
    init(_dots, usedots);

    for (const char* c = separators; *c != '\0'; c++) {
        if (*c > 0) _separator.set(*c, 1);
    }
    for (const unsigned char* uc = connectors; *uc != '\0'; uc++) {
        if (*uc > 0) _connector.set(*uc, 1);
    }
}

void SummaryConfig::init(std::string& cf, const char* str)
{
    bool escape = false;
    for (;str && *str != '\0'; str++) {
        if (!escape && *str == '\\') {
            escape = true;
        } else {
            if (escape) {
                // Allow space to be encoded as \_ (fsearchrc does not accept spaces..)
                if (*str == '_') {
                    cf += ' ';
                    escape = false;
                    continue;
                } else if (isxdigit(*str) && isxdigit(*(str+1))) {
                    cf += hexchar(str);
                    str++;
                    escape = false;
                    continue;
                } else {
                    escape = false;
                }
            }
            cf += *str;
        }
    }
}


ConfigFlag StringToConfigFlag(const char* confstring)
{
    if (strcmp(confstring, "off") == 0)
        return CF_OFF;
    if (strcmp(confstring, "on") == 0)
        return CF_ON;
    // default:
    return CF_AUTO;
}
