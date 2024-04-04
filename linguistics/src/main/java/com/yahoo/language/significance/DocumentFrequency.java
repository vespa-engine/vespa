package com.yahoo.language.significance;

public record DocumentFrequency(long frequency, long corpusSize) {

    public DocumentFrequency(long frequency, long corpusSize) {
        this.frequency = frequency;
        this.corpusSize = corpusSize;
    }
}
