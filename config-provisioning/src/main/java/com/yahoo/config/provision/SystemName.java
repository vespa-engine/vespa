package com.yahoo.config.provision;

/**
 * Systems in hosted Vespa
 *
 * @author mpolden
 */
public enum SystemName {

    /** System for continuous integration of latest Vespa and hosted Vespa code */
    ci,

    /** System for continuous deployment where a pre-test of hosted Vespa combined with a verified Vespa version */
    cd,

    /** Production system */
    main;

    public static SystemName defaultSystem() {
        return main;
    }

    public static SystemName from(String value) {
        switch (value) {
            case "ci": return ci;
            case "cd": return cd;
            case "main": return main;
            default: throw new IllegalArgumentException(String.format("'%s' is not a valid system", value));
        }
    }

}
