package com.yahoo.search.rendering;

/**
 * Controls how map/wset conversion should be applied when emitting Inspectors.
 */
class ConversionSettings {
    final boolean jsonDeepMaps;
    final boolean jsonWsets;
    final boolean jsonMapsAll;
    final boolean jsonWsetsAll;

    ConversionSettings(boolean jsonDeepMaps, boolean jsonWsets, boolean jsonMapsAll, boolean jsonWsetsAll) {
        this.jsonDeepMaps = jsonDeepMaps;
        this.jsonWsets = jsonWsets;
        this.jsonMapsAll = jsonMapsAll;
        this.jsonWsetsAll = jsonWsetsAll;
    }

    boolean convertDeep() {
        return jsonDeepMaps || jsonWsets;
    }
}
