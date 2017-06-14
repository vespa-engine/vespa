// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "palette.h"

#include <iostream>
#include <iomanip>

namespace storage {

namespace {
    struct Col {
        int16_t red;
        int16_t green;
        int16_t blue;

        Col(int16_t r, int16_t g, int16_t b) : red(r), green(g), blue(b) {}
    };

    std::vector<Col> createMainColors() {
        std::vector<Col> v;
        v.push_back(Col(128, 128, 128));
        v.push_back(Col(255, 0, 0));
        v.push_back(Col(255, 255, 0));
        v.push_back(Col(255, 0, 255));
        v.push_back(Col(0, 255, 0));
        v.push_back(Col(0, 255, 255));
        v.push_back(Col(0, 0, 255));
        v.push_back(Col(128, 64, 192));
        v.push_back(Col(192, 128, 64));
        v.push_back(Col(64, 192, 128));
        return v;
    }

    std::vector<Col> mainColors(createMainColors());
}

Palette::Palette(uint32_t colorCount)
{

    uint32_t variations = (colorCount + mainColors.size() - 1)
                        / (mainColors.size());
    int16_t darkvars = variations / 2;
    int16_t lightvars = (variations - 1) / 2;

    std::vector<Col> darkVars;
    if (darkvars > 0) {
        for (int32_t i=darkvars; i>0; --i) {
            for (uint32_t j=0; j<mainColors.size(); ++j) {
                Col& main(mainColors[j]);
                int rdiff = main.red / (darkvars + 1);
                int gdiff = main.green / (darkvars + 1);
                int bdiff = main.blue / (darkvars + 1);
                darkVars.push_back(Col(
                    std::max(0, main.red - rdiff * i),
                    std::max(0, main.green - gdiff * i),
                    std::max(0, main.blue - bdiff * i)));
            }
        }
    }
    std::vector<Col> lightVars;
    if (lightvars > 0) {
        for (int32_t i=1; i<=lightvars; ++i) {
            for (uint32_t j=0; j<mainColors.size(); ++j) {
                Col& main(mainColors[j]);
                int rdiff = (255 - main.red) / (lightvars + 1);
                int gdiff = (255 - main.green) / (lightvars + 1);
                int bdiff = (255 - main.blue) / (lightvars + 1);
                lightVars.push_back(Col(
                    std::min(255, main.red + rdiff * i),
                    std::min(255, main.green + gdiff * i),
                    std::min(255, main.blue + bdiff * i)));
            }
        }
    }
    for (std::vector<Col>::const_iterator it = darkVars.begin();
         it != darkVars.end(); ++it)
    {
        _colors.push_back((it->red << 16) | (it->green << 8) | it->blue);
    }
    for (std::vector<Col>::const_iterator it = mainColors.begin();
         it != mainColors.end(); ++it)
    {
        _colors.push_back((it->red << 16) | (it->green << 8) | it->blue);
    }
    for (std::vector<Col>::const_iterator it = lightVars.begin();
         it != lightVars.end(); ++it)
    {
        _colors.push_back((it->red << 16) | (it->green << 8) | it->blue);
    }
}

void
Palette::printHtmlTablePalette(std::ostream& out) const
{
    out << "<table>" << std::hex << std::setfill('0');
    uint32_t col = 0;
    while (col < _colors.size()) {
        out << "\n<tr>";
        for (uint32_t i=0; i<mainColors.size(); ++i) {
            out << "\n  <td bgcolor=\"#" << std::setw(6) << _colors[col++]
                << "\">";
            for (uint32_t j=0; j<6; ++j) out << "&nbsp;";
            out << "</td>";
        }
        out << "\n</tr>";
    }
    out << "\n</table>" << std::dec;
}

} // storage
