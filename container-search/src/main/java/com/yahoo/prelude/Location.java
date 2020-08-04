// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;
import java.util.StringTokenizer;

/**
 * Location data for a geographical query.
 *
 * @author Steinar Knutsen
 * @author arnej27959
 */
public class Location {

    // 1 or 2
    private int dimensions = 0;

    // line elements and rectangles
    private int x1 = 0;
    private int y1 = 0;
    private int x2 = 1;
    private int y2 = 1;

    // center(x,y), radius
    private int x = 1;
    private int y = 1;
    private int r = 1;

    // next three are now UNUSED
    // ranking table, rank multiplier (scale)
    // {0, 1} an int to make parsing and rendering the hit even simpler
    private int tableId = 0;
    private int s = 1;
    private int replace = 0;

    private boolean renderCircle = false;
    private boolean renderRectangle = false;
    private long aspect = 0;

    private String attribute;

    public boolean equals(Object other) {
        if (! (other instanceof Location)) return false;
        Location l = (Location)other;
        return dimensions == l.dimensions
            && renderCircle == l.renderCircle
            && renderRectangle == l.renderRectangle
            && aspect == l.aspect
            && x1 == l.x1
            && x2 == l.x2
            && y1 == l.y1
            && y2 == l.y2
            && x == l.x
            && y == l.y
            && r == l.r;
    }

    public boolean hasDimensions() {
        return dimensions != 0;
    }
    public void setDimensions(int d) {
        if (hasDimensions() && dimensions != d) {
            throw new IllegalArgumentException("already has dimensions="+dimensions+", cannot change it to "+d);
        }
        if (d == 1 || d == 2) {
            dimensions = d;
        } else {
            throw new IllegalArgumentException("Illegal location, dimensions must be 1 or 2, but was: "+d);
        }
    }
    public int getDimensions() {
        return dimensions;
    }

    // input data are degrees n/e (if positive) or s/w (if negative)
    public void setBoundingBox(double n, double s,
                               double e, double w)
    {
        setDimensions(2);
        if (hasBoundingBox()) {
            throw new IllegalArgumentException("can only set bounding box once");
        }
        int px1 = (int) (Math.round(w * 1000000));
        int px2 = (int) (Math.round(e * 1000000));
        int py1 = (int) (Math.round(s * 1000000));
        int py2 = (int) (Math.round(n * 1000000));
        if (px1 > px2) {
            throw new IllegalArgumentException("cannot have w > e");
        }
        x1 = px1;
        x2 = px2;
        if (py1 > py2) {
            throw new IllegalArgumentException("cannot have s > n");
        }
        y1 = py1;
        y2 = py2;
        renderRectangle = true;
    }

    private void adjustAspect() {
        //calculate aspect based on latitude (elevation angle)
        //no need to "optimize" for special cases, exactly 0, 30, 45, 60, or 90 degrees won't be input anyway
        double degrees = (double) y / 1000000d;
        if (degrees <= -90.0 || degrees >= +90.0) {
            aspect = 0;
            return;
        }
        double radians = degrees * Math.PI / 180d;
        double cosLatRadians = Math.cos(radians);
        aspect = (long) (cosLatRadians * 4294967295L);
    }

    public void setGeoCircle(double ns, double ew, double radius_in_degrees) {
        setDimensions(2);
        if (isGeoCircle()) {
            throw new IllegalArgumentException("can only set geo circle once");
        }
        int px = (int) (ew * 1000000);
        int py = (int) (ns * 1000000);
        int pr = (int) (radius_in_degrees * 1000000);
        if (ew < -180.1 || ew > +180.1) {
            throw new IllegalArgumentException("e/w location must be in range [-180,+180]");
        }
        if (ns < -90.1 || ns > +90.1) {
            throw new IllegalArgumentException("n/s location must be in range [-90,+90]");
        }
        if (radius_in_degrees < 0) {
            pr = -1;
        }
        x = px;
        y = py;
        r = pr;
        renderCircle = true;
        adjustAspect();
    }

    public void setXyCircle(int px, int py, int radius_in_units) {
        setDimensions(2);
        if (isGeoCircle()) {
            throw new IllegalArgumentException("can only set geo circle once");
        }
        if (radius_in_units < 0) {
            radius_in_units = -1;
        }
        x = px;
        y = py;
        r = radius_in_units;
        renderCircle = true;
    }

    private void parseRectangle(String rectangle) {
        int endof = rectangle.indexOf(']');
        if (endof == -1) {
            throw new IllegalArgumentException("Illegal location syntax: "+rectangle);
        }
        String rectPart = rectangle.substring(1,endof);
        StringTokenizer tokens = new StringTokenizer(rectPart, ",");
        setDimensions(Integer.parseInt(tokens.nextToken()));
        if (dimensions == 1) {
            x1 = Integer.parseInt(tokens.nextToken());
            x2 = Integer.parseInt(tokens.nextToken());
            if (tokens.hasMoreTokens()) {
                throw new IllegalArgumentException("Illegal location syntax: "+rectangle);
            }
        } else if (dimensions == 2) {
            x1 = Integer.parseInt(tokens.nextToken());
            y1 = Integer.parseInt(tokens.nextToken());
            x2 = Integer.parseInt(tokens.nextToken());
            y2 = Integer.parseInt(tokens.nextToken());
        }
        renderRectangle = true;
        String theRest = rectangle.substring(endof+1).trim();
        if (theRest.length() >= 15 && theRest.charAt(0) == '(') {
            parseCircle(theRest);
        }
    }

    private void parseCircle(String circle) {
        int endof = circle.indexOf(')');
        if (endof == -1) {
            throw new IllegalArgumentException("Illegal location syntax: "+circle);
        }
        String circlePart = circle.substring(1,endof);
        StringTokenizer tokens = new StringTokenizer(circlePart, ",");
        setDimensions(Integer.parseInt(tokens.nextToken()));
        x = Integer.parseInt(tokens.nextToken());
        if (dimensions == 2) {
            y = Integer.parseInt(tokens.nextToken());
        }
        r = Integer.parseInt(tokens.nextToken());
        Integer.parseInt(tokens.nextToken()); // was "tableId"
        Integer.parseInt(tokens.nextToken()); // was "scale" (multiplier)
        Integer.parseInt(tokens.nextToken()); // was "replace"

        if (dimensions == 1) {
            if (tokens.hasMoreTokens()) {
                throw new IllegalArgumentException("Illegal location syntax: "+circle);
            }
        }
        else {
            if (tokens.hasMoreTokens()) {
                String aspectToken = tokens.nextToken();
                if (aspectToken.equalsIgnoreCase("CalcLatLon")) {
                    adjustAspect();
                } else {
                    try {
                        aspect = Long.parseLong(aspectToken);
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("Aspect "+aspectToken+" for location must be an integer or 'CalcLatLon' for automatic aspect calculation.", nfe);
                    }
                    if (aspect > 4294967295L || aspect < 0) {
                        throw new IllegalArgumentException("Aspect "+aspect+" for location parameter must be less than 4294967296 (2^32)");
                    }
                }
            }
        }
        renderCircle = true;
        String theRest = circle.substring(endof+1).trim();
        if (theRest.length() > 5 && theRest.charAt(0) == '[') {
            parseRectangle(theRest);
        }
    }

    public Location() {}

    public Location(String rawLocation) {
        int attributeSepPos = rawLocation.indexOf(':');
        String locationSpec = rawLocation;
        if (attributeSepPos != -1) {
            String tempAttribute = rawLocation.substring(0, attributeSepPos);
            if (tempAttribute != null && !tempAttribute.isEmpty()) {
                attribute = tempAttribute;
            }
            locationSpec = rawLocation.substring(attributeSepPos+1);
        }

        if (locationSpec.charAt(0) == '[') {
            parseRectangle(locationSpec);
        }
        else if (locationSpec.charAt(0) == '(') {
            parseCircle(locationSpec);
        }
        else {
            throw new IllegalArgumentException("Illegal location syntax");
        }
    }

    public String toString() {
        return render(false);
    }
    public String backendString() {
        return render(true);
    }

    private String render(boolean forBackend) {
        StringBuilder ser = new StringBuilder();
        if (attribute != null) {
            ser.append(attribute).append(':');
        }
        if (renderRectangle) {
            ser.append("[").append(dimensions).append(",");
            if (dimensions == 1) {
                ser.append(x1).append(",").
                    append(x2);
            }
            else {
                ser.append(x1).append(",").
                    append(y1).append(",").
                    append(x2).append(",").
                    append(y2);
            }
            ser.append("]");
        }
        if (renderCircle) {
            ser.append("(").append(dimensions).append(",").append(x);
            if (dimensions == 2) {
                ser.append(",").append(y);
            }
            ser.append(",").append(forBackend ? backendRadius() : r).
                append(",").append(tableId).
                append(",").append(s).
                append(",").append(replace);
            if (dimensions == 2 && aspect != 0) {
                ser.append(",").append(aspect);
            }
            ser.append(")");
        }
        return ser.toString();
    }

    /**
     * Returns width of bounding box (actual width if rectangle, bounding square if circle)
     * @return width of bounding box
     */
    public int getBoundingWidth() {
        if (renderCircle) {
            return r * 2;
        } else {
            return x2 - x1;
        }
    }

    /**
     * Returns height of bounding box (actual height if rectangle, bounding square if circle)
     * @return height of bounding box
     */
    public int getBoundingHeight() {
        if (renderCircle) {
            return r * 2;
        } else {
            return y2 - y1;
        }
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean hasAttribute() {
        return attribute != null;
    }
    public String getAttribute() {
        return attribute;
    }
    public void setAttribute(String attributeName) {
        attribute = attributeName;
    }

    /** check whether this Location contains a 2D circle */
    public boolean isGeoCircle() {
        return (renderCircle && dimensions==2);
    }

    public boolean hasBoundingBox() {
        return renderRectangle;
    }

    private void checkGeoCircle() {
        if (!isGeoCircle()) {
            throw new IllegalArgumentException("only geo circles support this api");
        }
    }

    /**
     * Obtain degrees latitude (North-South direction); negative numbers are degrees South.
     * Expected range is [-90.0,+90.0] only.
     * May only be called when isGeoCircle() returns true.
     **/
    public double degNS() {
        checkGeoCircle();
        return 0.000001 * y;
    }

    /**
     * Obtain degrees longitude (East-West direction); negative numbers are degrees West.
     * Expected range is [-180.0,+180.0] only.
     * May only be called when isGeoCircle() returns true.
     **/
    public double degEW() {
        checkGeoCircle();
        return 0.000001 * x;
    }

    /**
     * Obtain circle radius (in degrees).
     * Note that "no radius" or "infinite radius" is represented as -1.
     * May only be called when isGeoCircle() returns true.
     **/
    public double degRadius() {
        checkGeoCircle();
        return (r < 0) ? -1.0 : (0.000001 * r);
    }

    private int backendRadius() {
        return (r < 0) ?  (512 * 1024 * 1024) : r;
    }

    /**
     * Encodes the location to the given buffer and returns the length.
     * For internal use.
     */
    public int encode(ByteBuffer buffer) {
        byte[] loc = Utf8.toBytes(backendString());
        buffer.put(loc);
        return loc.length;
    }


}
