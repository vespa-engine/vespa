// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

/**
 * The name of a species. For tracking purposes.
 * A name has the form superSpeciesName + "/" + serialNumber.generationNumber.
 *
 * @author bratseth
 */
public class SpeciesName {

    private final int level, serial, generation;

    private final String name, prefixName;

    private SpeciesName(int level, int serial, int generation, String prefixName) {
        this.level = level;
        this.serial = serial;
        this.generation = generation;
        this.prefixName = prefixName;
        if (level == 0)
            this.name = "";
        else
            this.name = prefixName + (prefixName.isEmpty() ? "" : "/") + serial + "." + generation;
    }

    /**
     * The level in the species hierarchy of the species having this name.
     * The root species has level 0.
     */
    public int level() { return level; }

    /** Returns the name of the root species: The empty string at level 0 */
    public static SpeciesName createRoot() {
        return new SpeciesName(0 ,0 ,0, "");
    }

    @Override
    public String toString() {
        if (level == 0) return "(root)";
        return name;
    }

    /** Returns the name of a new subspecies */
    public SpeciesName subspecies(int serial) {
        return new SpeciesName(level+1, serial, 0, name);
    }

    /** Returns the name of the successor of this species */
    public SpeciesName successor(int serial) {
        return new SpeciesName(level, serial, generation+1, prefixName);
    }

}
