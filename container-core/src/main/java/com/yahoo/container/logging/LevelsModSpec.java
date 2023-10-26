// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import java.util.LinkedHashMap;
import java.util.Map;

public class LevelsModSpec {
    private static final String ON = "on";
    private static final String OFF = "off";

    private static Map<String, String> defaultLogLevels() {
        var m = new LinkedHashMap<String,String>();
        m.put("fatal", ON);
        m.put("error", ON);
        m.put("warning", ON);
        m.put("info", ON);
        m.put("event", ON);
        m.put("config", ON);
        m.put("debug", OFF);
        m.put("spam", OFF);
        return m;
    }
    private Map<String, String> levelMods = defaultLogLevels();

    private void setAll(String value) {
        for (String k : levelMods.keySet()) {
            levelMods.put(k, value);
        }
    }
    private void setAll() {
        setAll(ON);
    }
    private void clearAll() {
        setAll(OFF);
    }

    public LevelsModSpec addModifications(String mods) {
        for (String s : mods.split("[+ ,]")) {
            String offOn = ON;
            if (s.startsWith("-")) {
                offOn = OFF;
                s = s.substring(1);
            }
            if (s.isEmpty()) continue;
            if (s.equals("all")) {
                setAll(offOn);
            } else if (levelMods.containsKey(s)) {
                levelMods.put(s, offOn);
            } else {
                throw new IllegalArgumentException("Unknown log level: "+s);
            }
        }
        return this;
    }

    public LevelsModSpec setLevels(String levels) {
        if (! (levels.startsWith("+") || levels.startsWith("-"))) {
            clearAll();
        }
        return addModifications(levels);
    }

    public String toLogctlModSpec() {
        var spec = new StringBuilder();
        boolean comma = false;
        for (var entry : levelMods.entrySet()) {
            if (comma) {
                spec.append(",");
            }
            spec.append(entry.getKey());
            spec.append("=");
            spec.append(entry.getValue());
            comma = true;
        }
        return spec.toString();
    }

}
