// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.geo;

/**
 * Class for parsing a bounding box in text format:
 * "n=37.44899,s=37.3323,e=-121.98241,w=-122.06566"
 *
 * <pre>
 * Input from:
 * http://gws.maps.yahoo.com/findlocation?q=sunnyvale,ca&amp;amp;flags=X
 * which gives this format:
 * &lt;boundingbox&gt;
 * &lt;north&gt;37.44899&lt;/north&gt;&lt;south&gt;37.3323&lt;/south&gt;&lt;east&gt;-121.98241&lt;/east&gt;&lt;west&gt;-122.06566&lt;/west&gt;
 * &lt;/boundingbox&gt;
 * it's also easy to use the geoplanet bounding box
 * &lt;boundingBox&gt;  
 * &lt;southWest&gt;  
 * &lt;latitude&gt;40.183868&lt;/latitude&gt;  
 * &lt;longitude&gt;-74.819519&lt;/longitude&gt;  
 * &lt;/southWest&gt;  
 * &lt;northEast&gt;  
 * &lt;latitude&gt;40.248291&lt;/latitude&gt;  
 * &lt;longitude&gt;-74.728798&lt;/longitude&gt;  
 * &lt;/northEast&gt;  
 * &lt;/boundingBox&gt;  
 * can be input as:
 * s=40.183868,w=-74.819519,n=40.248291,e=-74.728798
 * </pre>
 *
 * @author arnej27959
 */
public class BoundingBoxParser {

    // return variables
    public double n = 0.0;
    public double s = 0.0;
    public double e = 0.0;
    public double w = 0.0;

    /**
     * parse the given string as a bounding box and return a parser object with parsed coordinates in member variables
     * @throws IllegalArgumentException if the input is malformed in any way
     */
    public BoundingBoxParser(String bb) {
        this.parseString = bb;
        this.len = bb.length();
        parse();
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

    private boolean isCompassDirection(char ch) {
        return (ch == 'N' || ch == 'S' || ch == 'E' || ch == 'W' ||
                ch == 'n' || ch == 's' || ch == 'e' || ch == 'w');
    }

    private int lastNumStartPos = 0;

    private char nsew = 0;
    private boolean doneN = false;
    private boolean doneS = false;
    private boolean doneE = false;
    private boolean doneW = false;

    private void parse() {
        do {
            char ch = getNextChar();
            if (isCompassDirection(ch) && nsew == 0) {
                if (ch == 'n' || ch =='N') {
                    nsew = 'n';
                } else if (ch == 's' || ch == 'S') {
                    nsew = 's';
                } else if (ch == 'e' || ch == 'E') {
                    nsew = 'e';
                } else if (ch == 'w' || ch == 'W') {
                    nsew = 'w';
                }
                lastNumStartPos = 0;
            }
            if (ch == '=' || ch == ':') {
                if (nsew != 0) {
                    lastNumStartPos = pos;
                }
            }
            if (ch == ',' || ch == 0 || ch == ' ') {
                if (nsew != 0 && lastNumStartPos > 0) {
                    String sub = parseString.substring(lastNumStartPos, pos-1);
                    try {
                        double v = Double.parseDouble(sub);
                        if (nsew == 'n') {
                            if (doneN) {
                                throw new IllegalArgumentException("multiple limits for 'n' boundary");
                            }
                            n = v;
                            doneN = true;
                        } else if (nsew == 's') {
                            if (doneS) {
                                throw new IllegalArgumentException("multiple limits for 's' boundary");
                            }
                            s = v;
                            doneS = true;
                        } else if (nsew == 'e') {
                            if (doneE) {
                                throw new IllegalArgumentException("multiple limits for 'e' boundary");
                            }
                            e = v;
                            doneE = true;
                        } else if (nsew == 'w') {
                            if (doneW) {
                                throw new IllegalArgumentException("multiple limits for 'w' boundary");
                            }
                            w = v;
                            doneW = true;
                        }
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Could not parse "+nsew+" limit '"+sub+"' as a number");
                    }
                    nsew = 0;
                }
            }
        } while (pos <= len);

        if (doneN && doneS && doneE && doneW) {
            return;
        } else {
            throw new IllegalArgumentException("Missing bounding box limits, n="+doneN+" s="+doneS+" e="+doneE+" w="+doneW);
        }
    }

}

