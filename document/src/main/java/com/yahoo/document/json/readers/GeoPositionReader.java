// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.yahoo.document.PositionDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.json.TokenBuffer;

import static com.yahoo.document.json.readers.JsonParserHelpers.*;

/**
 * @author arnej
 */
public class GeoPositionReader {

    static void fillGeoPosition(TokenBuffer buffer, FieldValue positionFieldValue) {
        Double latitude = null;
        Double longitude = null;
        expectObjectStart(buffer.current());
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            String curName = buffer.currentName();
            if ("lat".equals(curName) || "latitude".equals(curName)) {
                latitude = readDouble(buffer) * 1.0e6;
            } else if ("lng".equals(curName) || "longitude".equals(curName)) {
                longitude = readDouble(buffer) * 1.0e6;
            } else if ("x".equals(curName)) {
                longitude = readDouble(buffer);
            } else if ("y".equals(curName)) {
                latitude = readDouble(buffer);
            } else {
                throw new IllegalArgumentException("Unexpected attribute "+curName+" in geo position field");
            }
        }
        expectObjectEnd(buffer.current());
        if (latitude == null) {
            throw new IllegalArgumentException("Missing 'lat' attribute in geo position field");
        }
        if (longitude == null) {
            throw new IllegalArgumentException("Missing 'lng' attribute in geo position field");
        }
        int y = (int) Math.round(latitude);
        int x = (int) Math.round(longitude);
        var geopos = PositionDataType.valueOf(x, y);
        positionFieldValue.assign(geopos);
    }

    private static double readDouble(TokenBuffer buffer) {
        try {
            return Double.parseDouble(buffer.currentText());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a number but got '" + buffer.currentText());
        }
    }

}
