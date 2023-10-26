// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "htmltable.h"

namespace storage {

Column::Column(const std::string& colName, HtmlTable* table)
    : _colName(colName), _alignment(RIGHT)
{
    if (table != 0) table->addColumn(*this);
}

Column::~Column() {}

HtmlTable::HtmlTable(const std::string& rowId)
    : _rowId(rowId), _columns(), _rows()
{}

HtmlTable::~HtmlTable() {}

void HtmlTable::print(std::ostream& out)
{
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

PercentageColumn::PercentageColumn(const std::string& colName, uint64_t total, HtmlTable* table)
    : ValueColumn<double>(colName, " %", table), _total(total),
      _values()
{
    if (total != 0) _totalIsAvg = true;
}

PercentageColumn::~PercentageColumn() {}

void
PercentageColumn::finalize() {
    uint64_t total = _total;
    if (total == 0) {
        for (const auto & entry : _values) {
            total += entry.second;
        }
    }
    for (const auto & entry : _values) {
        ValueColumn<double>::_values[entry.first] = 100.0 * entry.second / total;
    }
    ValueColumn<double>::finalize();
}


void
ByteSizeColumn::finalize() {
    uint64_t max = 0;
    for (const auto & entry : _values) {
        max = std::max(max, entry.second);
    }
    uint64_t oldMax = max;
    const char* type = "B";
    if (max > 10 * 1024) { max /= 1024; type = "kB"; }
    if (max > 10 * 1024) { max /= 1024; type = "MB"; }
    if (max > 10 * 1024) { max /= 1024; type = "GB"; }
    if (max > 10 * 1024) { max /= 1024; type = "TB"; }
    _denomination = std::pair<const char*, uint64_t>(type, max == 0 ? 1 : oldMax / max);
    ValueColumn<uint64_t>::finalize();
}

void
ByteSizeColumn::printValue(std::ostream& out, uint16_t row) {
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

template<typename T>
ValueColumn<T>::ValueColumn(const std::string& colName,
                            const std::string& denomination,
                            HtmlTable* table)
    : Column(colName, table), _values(), _denomination(denomination),
      _colorLimits(), _totalIsAvg(false)
{
    _valuePrinter << std::fixed << std::setprecision(2);
}

template<typename T>
ValueColumn<T>::~ValueColumn() {}

template<typename T>
void
ValueColumn<T>::finalize() {
    for (const auto & val : _values) {
        Color c = DEFAULT_COLOR;
        for (const auto & colorLimit : _colorLimits) {
            if (val.second <= colorLimit.first) {
                c = colorLimit.second;
                break;
            }
        }
        _colors[val.first] = c;
    }
    // Set color for total too.
    T total = getTotalValue();
    Color c = DEFAULT_COLOR;
    for (const auto & colorLimit : _colorLimits) {
        if (total <= colorLimit.first) {
            c = colorLimit.second;
            break;
        }
    }
    _colors[TOTAL] = c;
}

template<typename T>
T
ValueColumn<T>::getTotalValue() {
    T value = 0;
    for (const auto & val : _values) {
        value += val.second;
    }
    if (_totalIsAvg) value /= _values.size();
    return value;
}

template<typename T>
void
ValueColumn<T>::printValue(std::ostream& out, uint16_t row)  {
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

template struct ValueColumn<int64_t>;
template struct ValueColumn<uint64_t>;
template struct ValueColumn<double>;

}
