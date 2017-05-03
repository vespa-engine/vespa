// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::PieChart
 * \ingroup util
 *
 * \brief Helper library to print pie charts in HTML.
 */

#pragma once

#include <string>
#include <vector>
#include <ostream>

namespace storage {

class PieChart {
public:
    static double _minValue;

    enum ColorScheme {
        SCHEME_CUSTOM,
        SCHEME_RED,
        SCHEME_BLUE
    };
    enum Color {
        UNDEFINED = -1,
        BLACK     = 0x000000,
        RED       = 0xFF0000,
        GREEN     = 0x00FF00,
        BLUE      = 0x0000FF,
        WHITE     = 0xFFFFFF
    };
    struct Entry {
        double _value;
        std::string _name;
        int32_t _color;

        Entry(double val, const std::string& name, int32_t col);
    };

    static void printHtmlHeadAdditions(
            std::ostream& out, const std::string& indent = "");

private:
    const std::string _name;
    std::vector<Entry> _values;
    ColorScheme _colors;
    bool _printLabels;

public:
    PieChart(const std::string&, ColorScheme = SCHEME_BLUE);
    ~PieChart();

    void printLabels(bool doprint) { _printLabels = doprint; }

    void add(double value, const std::string& name);
    void add(double value, const std::string& name, Color c);
    void add(double value, const std::string& name, int32_t color);

    void printCanvas(std::ostream& out, uint32_t width, uint32_t height) const;
    void printScript(std::ostream& out, const std::string& indent = "") const;
};

} // storage

