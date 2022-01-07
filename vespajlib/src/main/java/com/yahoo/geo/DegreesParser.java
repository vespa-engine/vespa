// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.geo;

/**
 * Utility for parsing geographical coordinates
 *
 * @author arnej27959
 */
public class DegreesParser {

    /** The parsed latitude (degrees north if positive). */
    public double latitude = 0;

    /** The parsed longitude (degrees east if positive). */
    public double longitude = 0;

    private boolean isDigit(char ch) {
        return (ch >= '0' && ch <= '9');
    }
    private boolean isCompassDirection(char ch) {
        return (ch == 'N' || ch == 'S' || ch == 'E' || ch == 'W');
    }

    private final String parseString;
    private final int len;
    private int pos = 0;

    private char getNextChar() throws IllegalArgumentException {
        if (pos == len) {
            pos++;
            return 0;
        } else if (pos > len) {
            throw new IllegalArgumentException("position after end of string");
        } else {
            return parseString.charAt(pos++);
        }
    }

    /**
     * Parse the given string.
     *
     * The string must contain both a latitude and a longitude,
     * separated by a semicolon, in any order.  A latitude must
     * contain "N" or "S" and a number signifying degrees north or
     * south.  A longitude must contain "E" or "W" and a number
     * signifying degrees east or west.  No signs or spaces are
     * allowed.
     * <br>
     * Fractional degrees are recommended as the main input format,
     * but degrees plus fractional minutes may be used for testing.
     * You can use the degree sign (U+00B0 as seen in unicode at
     * http://www.unicode.org/charts/PDF/U0080.pdf) to separate
     * degrees from minutes, put the direction (NSEW) between as a
     * separator, or use a small letter 'o' as a replacement for the
     * degrees sign.
     * <br>
     * Some valid input formats: <br>
     * "N37.416383;W122.024683" → Sunnyvale <br>
     * "37N24.983;122W01.481" → same <br>
     * "N37\u00B024.983;W122\u00B001.481" → same <br>
     * "N63.418417;E10.433033" → Trondheim <br>
     * "N63o25.105;E10o25.982" → same <br>
     * "E10o25.982;N63o25.105" → same <br>
     * "N63.418417;E10.433033" → same <br>
     * "63N25.105;10E25.982" → same <br>
     *
     * @param latandlong Latitude and longitude separated by semicolon.
     */
    public DegreesParser(String latandlong) throws IllegalArgumentException {
        this.parseString = latandlong;
        this.len = parseString.length();

        char ch = getNextChar();

        boolean latSet = false;
        boolean longSet = false;

        double degrees = 0.0;
        double minutes = 0.0;
        double seconds = 0.0;
        boolean degSet = false;
        boolean minSet = false;
        boolean secSet = false;
        boolean dirSet = false;
        boolean foundDot = false;
        boolean foundDigits = false;

        boolean findingLatitude = false;
        boolean findingLongitude = false;

        double sign = 0.0;

        int lastpos = -1;

        do {
            boolean valid = false;
            if (pos == lastpos) {
                throw new RuntimeException("internal logic error at '"+parseString+"' pos:"+pos);
            } else {
                lastpos = pos;
            }

            // first, see if we can find some number
            double accum = 0.0;

            if (isDigit(ch) || ch == '.') {
                valid = true;
                double divider = 1.0;
                while (isDigit(ch)) {
                    foundDigits = true;
                    accum *= 10;
                    accum += (ch - '0');
                    ch = getNextChar();
                }
                if (ch == '.') {
                    foundDot = true;
                    ch = getNextChar();
                    while (isDigit(ch)) {
                        foundDigits = true;
                        accum *= 10;
                        accum += (ch - '0');
                        divider *= 10;
                        ch = getNextChar();
                    }
                }
                if (!foundDigits) {
                    throw new IllegalArgumentException("just a . is not a valid number");
                }
                accum /= divider;
            }

            // next, did we find a separator after the number?
            // degree sign is a separator after degrees, before minutes
            if (ch == '\u00B0' || ch == 'o') {
                valid = true;
                if (degSet) {
                    throw new IllegalArgumentException("degrees sign only valid just after degrees");
                }
                if (!foundDigits) {
                    throw new IllegalArgumentException("must have number before degrees sign");
                }
                if (foundDot) {
                    throw new IllegalArgumentException("cannot have fractional degrees before degrees sign");
                }
                ch = getNextChar();
            }
            // apostrophe is a separator after minutes, before seconds
            if (ch == '\'') {
                if (minSet || !degSet || !foundDigits) {
                    throw new IllegalArgumentException("minutes sign only valid just after minutes");
                }
                if (foundDot) {
                    throw new IllegalArgumentException("cannot have fractional minutes before minutes sign");
                }
                ch = getNextChar();
            }

            // if we found some number, assign it into the next unset variable
            if (foundDigits) {
                if (degSet) {
                    if (minSet) {
                        if (secSet) {
                            throw new IllegalArgumentException("extra number after full field");
                        } else {
                            seconds = accum;
                            secSet = true;
                        }
                    } else {
                        minutes = accum;
                        minSet = true;
                        if (foundDot) {
                            secSet = true;
                        }
                    }
                } else {
                    degrees = accum;
                    degSet = true;
                    if (foundDot) {
                        minSet = true;
                        secSet = true;
                    }
                }
                foundDot = false;
                foundDigits = false;
            }

            // there needs to be a direction (NSEW) somewhere, too
            if (isCompassDirection(ch)) {
                valid = true;
                if (dirSet) {
                    throw new IllegalArgumentException("already set direction once, cannot add direction: "+ch);
                }
                dirSet = true;
                if (ch == 'S' || ch == 'W') {
                    sign = -1;
                } else {
                    sign = 1;
                }
                if (ch == 'E' || ch == 'W') {
                    findingLongitude = true;
                } else {
                    findingLatitude = true;
                }
                ch = getNextChar();
            }

            // lastly, did we find the end-of-string or a separator between lat and long?
            if (ch == 0 || ch == ';' || ch == ' ') {
                valid = true;

                if (!dirSet) {
                    throw new IllegalArgumentException("end of field without any compass direction seen");
                }
                if (!degSet) {
                    throw new IllegalArgumentException("end of field without any number seen");
                }
                degrees += minutes / 60.0;
                degrees += seconds / 3600.0;
                degrees *= sign;

                if (findingLatitude) {
                    if (latSet) {
                        throw new IllegalArgumentException("found latitude (N or S) twice");
                    }
                    if (degrees < -90.0 || degrees > 90.0) {
                        throw new IllegalArgumentException("out of range [-90,+90]: "+degrees);
                    }
                    latitude = degrees;
                    latSet = true;
                } else if (findingLongitude) {
                    if (longSet) {
                        throw new IllegalArgumentException("found longitude (E or W) twice");
                    }
                    if (degrees < -180.0 || degrees > 180.0) {
                        throw new IllegalArgumentException("out of range [-180,+180]: "+degrees);
                    }
                    longitude = degrees;
                    longSet = true;
                } else {
                    throw new IllegalArgumentException("no direction found");
                }
                // reset
                degrees = 0.0;
                minutes = 0.0;
                seconds = 0.0;
                degSet = false;
                minSet = false;
                secSet = false;
                dirSet = false;
                foundDot = false;
                foundDigits = false;
                findingLatitude = false;
                findingLongitude = false;
                sign = 0.0;

                if (ch == 0) {
                    break;
                } else {
                    ch = getNextChar();
                }
            }

            if (!valid) {
                throw new IllegalArgumentException("invalid character: "+ch);
            }

        } while (ch != 0);

        if (!latSet) {
            throw new IllegalArgumentException("missing latitude");
        }
        if (!longSet) {
            throw new IllegalArgumentException("missing longitude");
        }
        // everything parsed OK
    }

}
