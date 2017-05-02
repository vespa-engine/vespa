// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::Graph
 * \ingroup util
 *
 * \brief Helper library to print graphs in HTML.
 */

#pragma once

#include <string>
#include <vector>
#include <ostream>

namespace storage {

class Graph {
public:
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
        WHITE     = 0xFFFFFF,
        YELLOW    = 0xFFFF00
    };
    struct Point {
        double x;
        double y;

        Point(double x_, double y_) : x(x_), y(y_) {}
    };
    struct Entry {
        std::vector<Point> points;
        std::string _name;
        int32_t _color;

        Entry(const std::vector<Point>& v, const std::string& name, int32_t col);
        Entry(Entry &&) = default;
        Entry & operator = (Entry &&) = default;
        ~Entry();
    };
    struct Axis {
        double value;
        std::string name;

        Axis(double val, const std::string& name_) : value(val), name(name_) {}
    };

    static void printHtmlHeadAdditions(
            std::ostream& out, const std::string& indent = "");

private:
    const std::string _name;
    std::vector<Entry> _graphs;
    ColorScheme _colors;
    std::vector<Axis> _xAxis;
    std::vector<Axis> _yAxis;
    uint32_t _leftPad;
    uint32_t _rightPad;
    uint32_t _topPad;
    uint32_t _bottomPad;
    uint32_t _legendXPos;
    uint32_t _legendYPos;

public:
    Graph(const std::string&, ColorScheme = SCHEME_BLUE);
    ~Graph();

    void add(const std::vector<Point>&, const std::string& name);
    void add(const std::vector<Point>&, const std::string& name, Color c);
    void add(const std::vector<Point>&, const std::string& name, int32_t color);

    void addXAxisLabel(double value, const std::string& name)
        { _xAxis.push_back(Axis(value, name)); }
    void addYAxisLabel(double value, const std::string& name)
        { _yAxis.push_back(Axis(value, name)); }

    void setBorders(uint32_t left, uint32_t right,
                    uint32_t top, uint32_t bottom)
    {
        _leftPad = left; _rightPad = right; _topPad = top; _bottomPad = bottom;
    }

    void setLegendPos(uint32_t left, uint32_t top)
        { _legendXPos = left; _legendYPos = top; }

    void printCanvas(std::ostream& out, uint32_t width, uint32_t height) const;
    void printScript(std::ostream& out, const std::string& indent = "") const;
};

} // storage

