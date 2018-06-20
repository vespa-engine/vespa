// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#pragma once
#include <string>

enum ConfigFlag {
    CF_OFF,
    CF_ON,
    CF_AUTO,
    CF_MAXVAL
};


/* Query highlight parameter class */

#ifndef _NEED_SUMMARY_CONFIG_IMPL
class SummaryConfig;
#else
#include <bitset>

class SummaryConfig
{
public:
    SummaryConfig(const char* hi_on, const char* hi_off,
                  const char* usedots, const char* separators,
                  const unsigned char* connectors,
                  ConfigFlag esc_markup,
                  ConfigFlag preserve_white_space_);

    ~SummaryConfig() {}

    inline const std::string & highlight_on()  const { return _highlight_on; }
    inline const std::string & highlight_off() const { return _highlight_off; }
    inline const std::string & dots()          const { return _dots; }
    inline bool separator(const char c) const { return (c < 0 ? false : _separator.test(c)); }
    inline bool connector(const unsigned char c) const { return _connector.test(c); }
    inline ConfigFlag escape_markup() const { return _escape_markup; }
    inline ConfigFlag preserve_white_space() const { return _preserve_white_space; }


protected:
    void init(std::string&, const char*);
private:
    std::string _highlight_on;
    std::string _highlight_off;
    std::string _dots;
    std::bitset<128> _separator; // Identify characters that should be removed in a teaser
    std::bitset<256> _connector; // Identify characters that connects two tokens into one
    ConfigFlag _escape_markup;
    ConfigFlag _preserve_white_space;
};

#endif


ConfigFlag StringToConfigFlag(const char* confstring);


SummaryConfig* CreateSummaryConfig(const char* highlight_on,
				   const char* highlight_off,
				   const char* dots,
				   const char* separators,
				   const unsigned char* connectors,
				   const ConfigFlag escape_markup = CF_AUTO,
				   const ConfigFlag preserve_white_space = CF_OFF);

void DeleteSummaryConfig(SummaryConfig*& sumconf);

