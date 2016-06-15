// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <iomanip>
#include <map>
#include <sstream>
#include <string>
#include <vector>
#include <memory>

namespace storage {

class HtmlTable;

struct Column {
    enum Color { DEFAULT_COLOR, LIGHT_GREEN, LIGHT_RED, LIGHT_YELLOW };
    enum Alignment { DEFAULT_ALIGNMENT, LEFT, CENTER, RIGHT };
    std::map<uint16_t, Color> _colors;
    std::string _colName;
    Alignment _alignment;
    enum { TOTAL = 0xffff };

    Column(const std::string& colName, HtmlTable* table = 0);
    virtual ~Column() {}

    virtual void finalize() {} // Called before print is issued

    static void printTdColor(std::ostream& out, Color c) {
        switch (c) {
            case LIGHT_GREEN: out << " bgcolor=\"#a0ffa0\""; break;
            case LIGHT_RED: out << " bgcolor=\"#ffa0a0\""; break;
            case LIGHT_YELLOW: out << " bgcolor=\"#ffffa0\""; break;
            case DEFAULT_COLOR: break;
        }
    }

    virtual void printElementStart(std::ostream& out, uint16_t row) {
        std::map<uint16_t, Color>::iterator color(_colors.find(row));
        out << "<td";
        if (color != _colors.end()) printTdColor(out, color->second);
        switch (_alignment) {
            case LEFT: out << " align=\"left\""; break;
            case CENTER: out << " align=\"center\""; break;
            case RIGHT: out << " align=\"right\""; break;
            case DEFAULT_ALIGNMENT: break;
        }
        out << ">";
    }
    virtual void printElementStop(std::ostream& out, uint16_t row) {
        std::map<uint16_t, Color>::iterator color(_colors.find(row));
        out << "</td>";
    }

    virtual void printElement(std::ostream& out, uint16_t row) {
        printElementStart(out, row);
        printValue(out, row);
        printElementStop(out, row);
    }

    virtual void printValue(std::ostream& out, uint16_t) {
        out << "&nbsp;";
    }
};

struct ColHeader {
    std::string _name;
    uint32_t _span;

    ColHeader(const std::string& name, uint32_t span)
        : _name(name), _span(span) {}
};

struct RowHeader {
    std::string _name;
    Column::Color _backgroundColor;

    RowHeader(const std::string& s)
        : _name(s), _backgroundColor(Column::DEFAULT_COLOR) {}
};

class HtmlTable {
    std::string _rowId;
    std::vector<Column*> _columns;
    std::vector<RowHeader> _rows;
    std::vector<ColHeader> _colHeaders;
    std::unique_ptr<std::string> _totalRow;

public:
    HtmlTable(const std::string& rowId)
        : _rowId(rowId), _columns(), _rows() {}

    void addTotalRow(const std::string& name)
        { _totalRow.reset(new std::string(name)); }
    void addColumnHeader(const std::string& name, uint32_t span)
        { _colHeaders.push_back(ColHeader(name, span)); }
    void addColumn(Column& col) { _columns.push_back(&col); }
    void addRow(const std::string& rowName) { _rows.push_back(rowName); }
    void addRow(uint64_t id)
        { std::ostringstream ost; ost << id; _rows.push_back(ost.str()); }
    void setRowHeaderColor(Column::Color c)
        { _rows.back()._backgroundColor = c; }
    uint32_t getRowCount() const { return _rows.size(); }

    inline void print(std::ostream& out);
};

inline Column::Column(const std::string& colName, HtmlTable* table)
    : _colName(colName), _alignment(RIGHT)
{
    if (table != 0) table->addColumn(*this);
}

/** Writes content just as you supply it. */
template<typename T>
struct ValueColumn : public Column {
    std::map<uint16_t, T> _values;
    std::string _denomination;
        // Show all values <=T as color.
    std::map<T, Color> _colorLimits;
    std::ostringstream _valuePrinter;
    bool _totalIsAvg;

    ValueColumn(const std::string& colName,
                const std::string& denomination = "",
                HtmlTable* table = 0)
        : Column(colName, table), _values(), _denomination(denomination),
          _colorLimits(), _totalIsAvg(false)
    {
        _valuePrinter << std::fixed << std::setprecision(2);
    }

    T& operator[](uint16_t row) { return _values[row]; }

    ValueColumn<T>& setPrecision(int precision)
        { _valuePrinter << std::setprecision(precision); return *this; }
    ValueColumn<T>& setTotalAsAverage(bool setAsAvg = true)
        { _totalIsAvg = setAsAvg; return *this; }

    void addColorLimit(T limit, Color c) {
        _colorLimits[limit] = c;
    }

    virtual void finalize() {
        for (typename std::map<uint16_t, T>::iterator val = _values.begin();
             val != _values.end(); ++val)
        {
            Color c = DEFAULT_COLOR;
            for (typename std::map<T, Color>::iterator it
                    = _colorLimits.begin(); it != _colorLimits.end(); ++it)
            {
                if (val->second <= it->first) {
                    c = it->second;
                    break;
                }
            }
            _colors[val->first] = c;
        }
            // Set color for total too.
        T total = getTotalValue();
        Color c = DEFAULT_COLOR;
        for (typename std::map<T, Color>::iterator it
                = _colorLimits.begin(); it != _colorLimits.end(); ++it)
        {
            if (total <= it->first) {
                c = it->second;
                break;
            }
        }
        _colors[TOTAL] = c;
    }

    virtual T getTotalValue() {
        T value = 0;
        for (typename std::map<uint16_t, T>::iterator val = _values.begin();
             val != _values.end(); ++val)
        {
            value += val->second;
        }
        if (_totalIsAvg) value /= _values.size();
        return value;
    }

    virtual void printValue(std::ostream& out, uint16_t row) {
        T value;
        if (row == TOTAL) {
            value = getTotalValue();
        } else {
            typename std::map<uint16_t, T>::iterator val = _values.find(row);
            if (val == _values.end()) {
                Column::printValue(out, row);
                return;
            }
            value = val->second;
        }
        _valuePrinter.str("");
        _valuePrinter << value << _denomination;
        out << _valuePrinter.str();
    }
};

/** Writes content as percentage of a total */
struct PercentageColumn : public ValueColumn<double> {
    uint64_t _total;
    std::map<uint16_t, uint64_t> _values;

    PercentageColumn(const std::string& colName, uint64_t total = 0,
                     HtmlTable* table = 0)
        : ValueColumn<double>(colName, " %", table), _total(total),
          _values()
    {
        if (total != 0) _totalIsAvg = true;
    }

    virtual void finalize() {
        uint64_t total = _total;
        if (total == 0) {
            for (std::map<uint16_t, uint64_t>::iterator it = _values.begin();
                 it != _values.end(); ++it)
            {
                total += it->second;
            }
        }
        for (std::map<uint16_t, uint64_t>::iterator it = _values.begin();
             it != _values.end(); ++it)
        {
            ValueColumn<double>::_values[it->first]
                    = 100.0 * it->second / total;
        }
        ValueColumn<double>::finalize();
    }

    uint64_t& operator[](uint16_t row) { return _values[row]; }
};

/** Writes content as a byte size, using an appropriate size. */
struct ByteSizeColumn : public ValueColumn<uint64_t> {
    std::pair<const char*, uint64_t> _denomination;

    ByteSizeColumn(const std::string& colName, HtmlTable* table = 0)
        : ValueColumn<uint64_t>(colName, "", table) {}

    uint64_t& operator[](uint16_t row) { return _values[row]; }

    virtual void finalize() {
        uint64_t max = 0;
        for (std::map<uint16_t, uint64_t>::iterator it = _values.begin();
             it != _values.end(); ++it)
        {
            max = std::max(max, it->second);
        }
        uint64_t oldMax = max;
        const char* type = "B";
        if (max > 10 * 1024) { max /= 1024; type = "kB"; }
        if (max > 10 * 1024) { max /= 1024; type = "MB"; }
        if (max > 10 * 1024) { max /= 1024; type = "GB"; }
        if (max > 10 * 1024) { max /= 1024; type = "TB"; }
        _denomination = std::pair<const char*, uint64_t>(
                            type, max == 0 ? 1 : oldMax / max);
        ValueColumn<uint64_t>::finalize();
    }

    virtual void printValue(std::ostream& out, uint16_t row) {
        uint64_t value;
        if (row == TOTAL) {
            value = getTotalValue();
        } else {
            std::map<uint16_t, uint64_t>::iterator val(_values.find(row));
            if (val == _values.end()) {
                Column::printValue(out, row);
                return;
            }
            value = val->second;
        }
        out << (value / _denomination.second) << ' ' << _denomination.first;
    }
};

typedef ValueColumn<int64_t>     LongColumn;
typedef ValueColumn<double>      DoubleColumn;

inline void HtmlTable::print(std::ostream& out) {
    out << "<table border=\"1\" cellpadding=\"2\" cellspacing=\"0\">\n<tr><th";
    if (!_colHeaders.empty()) out << " rowspan=\"2\"";
    out << ">" << _rowId << "</th>";
    if (!_colHeaders.empty()) {
        for (uint32_t i=0; i<_colHeaders.size(); ++i) {
            out << "<th colspan=\"" << _colHeaders[i]._span << "\">"
                << _colHeaders[i]._name << "</th>";
        }
        out << "</tr>\n";
    }
    for (uint32_t i=0; i<_columns.size(); ++i) {
        _columns[i]->finalize();
        out << "<th>" << _columns[i]->_colName << "</th>";
    }
    out << "</tr>\n";
    for (uint32_t i=0; i<_rows.size(); ++i) {
        out << "<tr><td";
        Column::printTdColor(out, _rows[i]._backgroundColor);
        out << ">" << _rows[i]._name << "</td>";
        for (uint32_t j=0; j<_columns.size(); ++j) {
            _columns[j]->printElement(out, i);
        }
        out << "</tr>\n";
    }
    if (_totalRow.get()) {
        out << "<tr><td>" << *_totalRow << "</td>";
        for (uint32_t j=0; j<_columns.size(); ++j) {
            _columns[j]->printElement(out, Column::TOTAL);
        }
        out << "</tr>\n";
    }
    out << "</table>\n";
}

} // storage

