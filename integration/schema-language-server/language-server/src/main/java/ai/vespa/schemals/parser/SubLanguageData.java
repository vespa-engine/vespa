package ai.vespa.schemals.parser;

/**
 * SubLanguageData represents another language embedded in schema language.
 * It store the content to parse in another parser and the number of stripped chars in front.
 */
public record SubLanguageData(String content, int leadingStripped) {
}
