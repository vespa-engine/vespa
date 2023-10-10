// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    virtual ~Column();

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
    virtual void printElementStop(std::ostream& out, [[maybe_unused]] uint16_t row) {
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
    HtmlTable(const std::string& rowId);
    ~HtmlTable();

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

    void print(std::ostream& out);
};

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
                HtmlTable* table = 0);
    ~ValueColumn();

    T& operator[](uint16_t row) { return _values[row]; }

    ValueColumn<T>& setPrecision(int precision) {
        _valuePrinter << std::setprecision(precision);
        return *this;
    }
    ValueColumn<T>& setTotalAsAverage(bool setAsAvg = true) {
        _totalIsAvg = setAsAvg;
        return *this;
    }

    void addColorLimit(T limit, Color c) {
        _colorLimits[limit] = c;
    }

    void finalize() override;
    virtual T getTotalValue();
    void printValue(std::ostream& out, uint16_t row) override;
};

/** Writes content as percentage of a total */
struct PercentageColumn : public ValueColumn<double> {
    uint64_t _total;
    std::map<uint16_t, uint64_t> _values;

    PercentageColumn(const std::string& colName, uint64_t total = 0, HtmlTable* table = 0);
    ~PercentageColumn();

    void finalize() override;

    uint64_t& operator[](uint16_t row) { return _values[row]; }
};

/** Writes content as a byte size, using an appropriate size. */
struct ByteSizeColumn : public ValueColumn<uint64_t> {
    std::pair<const char*, uint64_t> _denomination;

    ByteSizeColumn(const std::string& colName, HtmlTable* table = 0)
        : ValueColumn<uint64_t>(colName, "", table) {}

    uint64_t& operator[](uint16_t row) { return _values[row]; }
    void finalize() override;
    void printValue(std::ostream& out, uint16_t row) override;
};

using LongColumn = ValueColumn<int64_t>;
using DoubleColumn = ValueColumn<double>;

} // storage
