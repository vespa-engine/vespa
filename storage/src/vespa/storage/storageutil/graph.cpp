// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "graph.h"
#include <vespa/vespalib/util/exceptions.h>
#include <iomanip>

namespace storage {

Graph::Entry::Entry(const std::vector<Point>& v, const std::string& name, int32_t col)
    : points(v),
      _name(name),
      _color(col)
{}

    Graph::Entry::~Entry() {}

void
Graph::printHtmlHeadAdditions(std::ostream& out, const std::string& indent)
{
    (void) out;
    (void) indent;
    // FIXME this used to reference Yahoo-internal JS URIs
}

Graph::Graph(const std::string& name, ColorScheme cs)
  : _name(name),
    _graphs(),
    _colors(cs),
    _leftPad(50),
    _rightPad(0),
    _topPad(0),
    _bottomPad(0)
{}

Graph::~Graph() {}

void
Graph::add(const std::vector<Point>& values, const std::string& name)
{
    if (_colors == SCHEME_CUSTOM) {
        throw vespalib::IllegalArgumentException(
                "Using custom color scheme you need to supply a color for each "
                "graph.", VESPA_STRLOC);
    }
    _graphs.push_back(Entry(values, name, UNDEFINED));
}

void
Graph::add(const std::vector<Point>& values, const std::string& name, Color c)
{
    if (_colors != SCHEME_CUSTOM) {
        throw vespalib::IllegalArgumentException(
                "Not using custom color scheme you cannot supply a custom "
                "color for a graph.", VESPA_STRLOC);
    }
    _graphs.push_back(Entry(values, name, c));
}

void
Graph::add(const std::vector<Point>& values, const std::string& name, int32_t c)
{
    if (_colors != SCHEME_CUSTOM) {
        throw vespalib::IllegalArgumentException(
                "Not using custom color scheme you cannot supply a custom "
                "color for a graph.", VESPA_STRLOC);
    }
    _graphs.push_back(Entry(values, name, (Color) c));
}

void
Graph::printCanvas(std::ostream& out, uint32_t width, uint32_t height) const
{
    out << "<div><canvas id=\"" << _name << "\" width=\"" << width
        << "\" height=\"" << height << "\"/></div>";
}

namespace {
    void printDatasetDefinition(std::ostream& o, const std::string& i,
            const std::string& n, const std::vector<Graph::Entry>& e)
    {
        o << i << "  var " << n << "_dataset = {\n" << std::dec;
        bool first = true;
        for (std::vector<Graph::Entry>::const_iterator it = e.begin();
             it != e.end(); ++it)
        {
            if (!first) o << ",\n";
            first = false;
            o << i << "      '" << it->_name << "': [";
            for (uint32_t j=0; j<it->points.size(); ++j) {
                if (j != 0) o << ", ";
                o << "[" << it->points[j].x << ", " << it->points[j].y << "]";
            }
            o << "]";
        }
        o << "\n" << i << "  };";
    }

    void printCustomColorScheme(std::ostream& o, const std::string& i,
            const std::string& n, const std::vector<Graph::Entry>& e)
    {
        o << "  var " << n << "_customScheme = new Hash({\n" << std::hex;
        bool first = true;
        for (std::vector<Graph::Entry>::const_iterator it = e.begin();
             it != e.end(); ++it)
        {
            if (!first) o << ",\n";
            first = false;
            o << i << "      '" << it->_name << "': '#" << std::setw(6)
              << std::setfill('0') << (it->_color & 0x00FFFFFF) << "'";
        }
        o << "\n" << i << "  });" << std::dec;
    }

    void printOptions(std::ostream& o, const std::string& i,
            const std::string& n, Graph::ColorScheme c,
            const std::vector<Graph::Axis>& xAxis,
            const std::vector<Graph::Axis>& yAxis,
            uint32_t leftpad, uint32_t rightpad,
            uint32_t toppad, uint32_t bottompad,
            uint32_t legendXPos, uint32_t legendYPos)
    {
        o <<      "  var " << n << "_options = {\n"
          << i << "    padding: {\n"
          << i << "      left: " << leftpad << ",\n"
          << i << "      right: " << rightpad << ",\n"
          << i << "      top: " << toppad << ",\n"
          << i << "      bottom: " << bottompad << ",\n"
          << i << "    },\n"
          << i << "    background: {\n"
          << i << "      color: '#ffffff'\n"
          << i << "    },\n"
          << i << "    shouldFill: true,\n";
        if (c == Graph::SCHEME_CUSTOM) {
            o << i << "    \"colorScheme\": " << n << "_customScheme,\n";
        } else {
            o << i << "    colorScheme: '";
            switch (c) {
                case Graph::SCHEME_RED: o << "red"; break;
                case Graph::SCHEME_BLUE: o << "blue"; break;
                case Graph::SCHEME_CUSTOM: break;
            }
            o << "',\n";
        }
        o << i << "    legend: {\n"
          << i << "      opacity: 0.9,\n"
          << i << "      position: {\n"
          << i << "        top: " << legendYPos << ",\n"
          << i << "        left: " << legendXPos << "\n"
          << i << "      }\n"
          << i << "    },\n"
          << i << "    axis: {\n"
          << i << "      labelColor: '#000000',\n"
          << i << "      x: {\n";
        if (xAxis.size() > 0) {
            o << i << "        ticks: [\n";
            for (uint32_t j=0; j<xAxis.size(); ++j) {
                o << i << "          {v:" << xAxis[j].value << ", label:'"
                  << xAxis[j].name << "'},\n";
            }
            o << i << "        ]\n";
        }
        o << i << "      },\n"
          << i << "      y: {\n";
        if (yAxis.size() > 0) {
            o << i << "        ticks: [\n";
            for (uint32_t j=0; j<yAxis.size(); ++j) {
                o << i << "          {v:" << yAxis[j].value << ", label:'"
                  << yAxis[j].name << "'},\n";
            }
            o << i << "        ]\n";
        }

        o << i << "      }\n"
          << i << "    }\n"
          << i << "  };";
    }

    void printChart(std::ostream& o, const std::string& i, const std::string& n)
    {
        o <<      "  var " << n << "_chart = new Plotr.LineChart('" << n
                << "', " << n << "_options);\n"
          << i << "  " << n << "_chart.addDataset(" << n << "_dataset);\n"
          << i << "  " << n << "_chart.render();";
    }
}

void
Graph::printScript(std::ostream& out, const std::string& indent) const
{
    out << "<script type=\"text/javascript\">\n";
    printDatasetDefinition(out, indent, _name, _graphs);
    if (_colors == SCHEME_CUSTOM) {
        out << "\n" << indent;
        printCustomColorScheme(out, indent, _name, _graphs);
    }
    out << "\n" << indent;
    printOptions(out, indent, _name, _colors, _xAxis, _yAxis,
                 _leftPad, _rightPad, _topPad, _bottomPad,
                 _legendXPos, _legendYPos);
    out << "\n" << indent;
    printChart(out, indent, _name);
    out << "\n" << indent << "</script>";
}

} // storage

