// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.prelude.Location;
import java.nio.ByteBuffer;


/**
 * This represents a geo-location for matching.
 * Note that this won't produce summary fields.
 *
 * @author  arnej
 */
public class GeoLocationItem extends TermItem {

    private Location location;

    /**
     */
    public GeoLocationItem(Location location) {
        super(location.getAttribute(), false);
        this.location = location.clone();
        if (! location.hasAttribute()) {
            throw new IllegalArgumentException("missing attribute on location: "+location);
        }
        location.setAttribute(null);
        setNormalizable(false);
    }

    /**
     */
    public GeoLocationItem(Location location, String indexName) {
        super(indexName, false);
        this.location = location.clone();
        if (location.hasAttribute() && ! location.getAttribute().equals(indexName)) {
            throw new IllegalArgumentException("inconsistent attribute on location: "+location+" versus indexName: "+indexName);
        }
        location.setAttribute(null);
        setNormalizable(false);
    }

    @Override
    public String getRawWord() {
        return stringValue();
    }

    @Override
    public ItemType getItemType() {
        return ItemType.LOCATION_TERM;
    }

    @Override
    public String getName() {
        return "LOCATION";
    }

    @Override
    public String stringValue() {
        location.setAttribute(null);
        return location.toString();
    }

    @Override
    public void setValue(String value) {
        throw new UnsupportedOperationException("Cannot setValue("+value+") on "+getName());
    }

    @Override
    public int hashCode() {
        return super.hashCode() + 199 * location.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if ( ! super.equals(object)) return false;
        GeoLocationItem other = (GeoLocationItem) object; // Ensured by superclass
        if ( ! location.equals(other.location)) return false;
        return true;
    }

    @Override
    public String getIndexedString() {
        location.setAttribute(null);
        return location.toString();
    }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer); // takes care of index bytes
        putString(getIndexedString(), buffer);
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
