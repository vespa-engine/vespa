// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "piechart.h"

#include <iomanip>
#include <vespa/vespalib/util/exceptions.h>

namespace storage {

double PieChart::_minValue = 0.0000001;

PieChart::Entry::Entry(double val, const std::string& name, int32_t col)
  : _value(val), _name(name), _color(col)
{
}

void
PieChart::printHtmlHeadAdditions(std::ostream& out, const std::string& indent)
{
    (void) out;
    (void) indent;
    // FIXME this used to reference Yahoo-internal JS URIs.
    // Deprecated functionality either way.
}

PieChart::PieChart(const std::string& name, ColorScheme cs)
  : _name(name),
    _values(),
    _colors(cs),
    _printLabels(true)
{}

PieChart::~PieChart() {}

void
PieChart::add(double value, const std::string& name)
{
    if (value < _minValue) {
        std::ostringstream ost;
        ost << "Value of " << value << " is below the minimum supported value "
            << "of the pie chart (" << _minValue << ")";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    if (_colors == SCHEME_CUSTOM) {
        throw vespalib::IllegalArgumentException(
                "Using custom color scheme you need to supply a color for each "
                "value.", VESPA_STRLOC);
    }
    _values.push_back(Entry(value, name, UNDEFINED));
}

void
PieChart::add(double value, const std::string& name, Color c)
{
    if (value < _minValue) {
        std::ostringstream ost;
        ost << "Value of " << value << " is below the minimum supported value "
            << "of the pie chart (" << _minValue << ")";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    if (_colors != SCHEME_CUSTOM) {
        throw vespalib::IllegalArgumentException(
                "Not using custom color scheme you cannot supply a custom "
                "color for a value.", VESPA_STRLOC);
    }
    _values.push_back(Entry(value, name, c));
}

void
PieChart::add(double value, const std::string& name, int32_t color)
{
    if (value < _minValue) {
        std::ostringstream ost;
        ost << "Value of " << value << " is below the minimum supported value "
            << "of the pie chart (" << _minValue << ")";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    if (_colors != SCHEME_CUSTOM) {
        throw vespalib::IllegalArgumentException(
                "Not using custom color scheme you cannot supply a custom "
                "color for a value.", VESPA_STRLOC);
    }
    _values.push_back(Entry(value, name, (Color) color));
}

void
PieChart::printCanvas(std::ostream& out, uint32_t width, uint32_t height) const
{
    out << "<div><canvas id=\"" << _name << "\" width=\"" << width
        << "\" height=\"" << height << "\"/></div>";
}

namespace {
    void printDatasetDefinition(std::ostream& o, const std::string& i,
            const std::string& n, const std::vector<PieChart::Entry>& e)
    {
        o << i << "  var " << n << "_dataset = {\n" << std::dec;
        bool first = true;
        for (std::vector<PieChart::Entry>::const_iterator it = e.begin();
             it != e.end(); ++it)
        {
            if (!first) o << ",\n";
            first = false;
            o << i << "      '" << it->_name << "': [[0," << it->_value
                << "]]";
        }
        o << "\n" << i << "  };";
    }

    void printCustomColorScheme(std::ostream& o, const std::string& i,
            const std::string& n, const std::vector<PieChart::Entry>& e)
    {
        o << "  var " << n << "_customScheme = new Hash({\n" << std::hex;
        bool first = true;
        for (std::vector<PieChart::Entry>::const_iterator it = e.begin();
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
            const std::string& n, const std::vector<PieChart::Entry>& e,
            PieChart::ColorScheme c, bool printLabels)
    {
        o <<      "  var " << n << "_options = {\n"
          << i << "    padding: {\n"
          << i << "      left: 0,\n"
          << i << "      right: 0,\n"
          << i << "      top: 0,\n"
          << i << "      bottom: 0,\n"
          << i << "    },\n"
          << i << "    background: {\n"
          << i << "      color: '#ffffff'\n"
          << i << "    },\n"
          << i << "    pieRadius: '0.4',\n";
        if (c == PieChart::SCHEME_CUSTOM) {
            o << i << "    \"colorScheme\": " << n << "_customScheme,\n";
        } else {
            o << i << "    colorScheme: '";
            switch (c) {
                case PieChart::SCHEME_RED: o << "red"; break;
                case PieChart::SCHEME_BLUE: o << "blue"; break;
                case PieChart::SCHEME_CUSTOM: break;
            }
            o << "',\n";
        }
        o << i << "    axis: {\n"
          << i << "      labelColor: '#000000',\n"
          << i << "      x: {\n";
        if (!printLabels) {
            o << i << "        hide: true,\n";
        }
        o << i << "        ticks: [\n";
        bool first = true;
        uint32_t tmp = 0;
        for (std::vector<PieChart::Entry>::const_iterator it = e.begin();
             it != e.end(); ++it)
        {
            if (!first) o << ",\n";
            first = false;
            o << i << "          {v:" << tmp++ << ", label:'" << it->_name
               << "'}";
        }
        o << "\n" << i << "        ]\n";
        o << i << "      }\n"
          << i << "    }\n"
          << i << "  };";
    }

    void printPie(std::ostream& o, const std::string& i, const std::string& n)
    {
        o <<      "  var " << n << "_pie = new Plotr.PieChart('" << n << "', "
               << n << "_options);\n"
          << i << "  " << n << "_pie.addDataset(" << n << "_dataset);\n"
          << i << "  " << n << "_pie.render();";
    }
}

void
PieChart::printScript(std::ostream& out, const std::string& indent) const
{
    out << "<script type=\"text/javascript\">\n";
    printDatasetDefinition(out, indent, _name, _values);
    if (_colors == SCHEME_CUSTOM) {
        out << "\n" << indent;
        printCustomColorScheme(out, indent, _name, _values);
    }
    out << "\n" << indent;
    printOptions(out, indent, _name, _values, _colors, _printLabels);
    out << "\n" << indent;
    printPie(out, indent, _name);
    out << "\n" << indent << "</script>";
}

} // storage

