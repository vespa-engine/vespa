// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.query;

import com.yahoo.prelude.Location;
import java.nio.ByteBuffer;

/**
 * This represents a geo-location in the query tree.
 * Used for closeness(fieldname) and distance(fieldname) rank features.
 *
 * @author arnej
 */
public class GeoLocationItem extends TermItem {

    private Location location;

    /**
     * Construct from a Location, which must be geo circle with an attribute set.
     */
    public GeoLocationItem(Location location) {
        this(location, location.getAttribute());
        if (! location.hasAttribute()) {
            throw new IllegalArgumentException("Missing attribute on location: " + location);
        }
    }

    /**
     * Construct from a Location and a field name.
     * The Location must be a geo circle.
     * If the Location has an attribute set, it must match the field name.
     */
    public GeoLocationItem(Location location, String fieldName) {
        super(fieldName, false);
        if (location.hasAttribute() && ! location.getAttribute().equals(fieldName)) {
            throw new IllegalArgumentException("Inconsistent attribute on location: " + location.getAttribute() +
                                               " versus fieldName: " + fieldName);
        }
        if (! location.isGeoCircle()) {
            throw new IllegalArgumentException("GeoLocationItem only supports Geo Circles, got: " + location);
        }
        if (location.hasBoundingBox()) {
            throw new IllegalArgumentException("GeoLocationItem does not support bounding box, got: " + location);
        }
        this.location = new Location(location.toString());
        this.location.setAttribute(null); // keep this in (superclass) indexName only
        setNormalizable(false);
    }

    public Location getLocation() {
        return location;
    }

    @Override
    public String getRawWord() {
        return stringValue();
    }

    @Override
    public ItemType getItemType() {
        return ItemType.GEO_LOCATION_TERM;
    }

    @Override
    public String getName() {
        return "GEO_LOCATION";
    }

    @Override
    public String stringValue() {
        return location.toString();
    }

    @Override
    public void setValue(String value) {
        throw new UnsupportedOperationException("Cannot setValue("+value+") on "+getName());
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), location);
    }

    @Override
    public boolean equals(Object object) {
        if ( ! super.equals(object)) return false;
        GeoLocationItem other = (GeoLocationItem) object; // Ensured by superclass
        if ( ! location.equals(other.location)) return false;
        return true;
    }

    @Override
    public GeoLocationItem clone() {
        var clone = (GeoLocationItem)super.clone();
        clone.location = this.location.clone();
        return clone;
    }

    @Override
    public String getIndexedString() {
        return location.toString();
    }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer); // takes care of index bytes
        // TODO: use a better format for encoding the location on the wire.
        putString(location.backendString(), buffer);
    }

    @Override
    public int getNumWords() {
        return 1;
    }

    @Override
    public boolean isStemmed() {
        return true;
    }

    @Override
    public boolean isWords() {
        return false;
    }

}
