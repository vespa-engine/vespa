package com.yahoo.config.provision;

public class ZoneId {

    protected final Environment environment;
    protected final RegionName region;

    public ZoneId(Environment environment, RegionName region) {
        this.environment = environment;
        this.region = region;
    }

    /** Returns the current environment */
    public Environment environment() { return environment; }

    /** Returns the current region */
    public RegionName region() { return region; }

    @Override
    public String toString() {
        return "zone " + environment + "." + region;
    }

    @Override
    public int hashCode() { return environment().hashCode() + 7 * region.hashCode();}

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Zone)) return false;

        ZoneId other = (ZoneId)o;
        if ( this.environment() != other.environment()) return false;
        if ( ! this.region.equals(other.region)) return false;
        return true;
    }
}
