package com.yahoo.container.jdisc.secret;

import java.util.Objects;

public class Key {

    private final String keyGroup;
    private final String keyName;

    public Key(String keyGroup, String keyName) {
        this.keyGroup = keyGroup;
        this.keyName = keyName;
    }

    public String keyGroup() {
        return keyGroup;
    }

    public String keyName() {
        return keyName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Key that = (Key) o;
        if ( ! (that.keyGroup.equals(keyGroup))) return false;
        if ( ! (that.keyName.equals(keyName))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyGroup, keyName);
    }

    @Override
    public String toString() { return "key group: " + keyGroup + ", key name: " + keyName; }

}
