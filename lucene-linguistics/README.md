<!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
# Vespa Lucene Linguistics

Linguistics implementation based on the [Apache Lucene](https://lucene.apache.org).

Features:

- a list of default analyzers per language;
- building custom analyzers through the configuration of the linguistics component;
- building custom analyzers in Java code and declaring them as `components`.

## Development

Build:

```shell
mvn clean test -U package
```

To compile configuration classes so that Intellij doesn't complain:

- right click on `pom.xml`
- then `Maven`
- then `Generate Sources and Update Folders`

## Usage

Add `<component>` to `services.xml` of your application package, e.g.:

```xml
<component id="com.yahoo.language.lucene.LuceneLinguistics" bundle="lucene-linguistics">
  <config name="com.yahoo.language.lucene.lucene-analysis">
    <configDir>linguistics</configDir>
    <analysis>
      <item key="en">
        <tokenizer>
          <name>standard</name>
        </tokenizer>
        <tokenFilters>
          <item>
            <name>reverseString</name>
          </item>
        </tokenFilters>
      </item>
    </analysis>
  </config>
</component>
```

into `container` clusters that have `<document-processing/>` and/or `<search>` specified.

And then package and deploy, e.g.:

```shell
(mvn clean -DskipTests=true -U package && vespa deploy -w 100)
```

### Configuration of Lucene Analyzers

Read the Lucene docs of subclasses of:

- [TokenizerFactory](https://lucene.apache.org/core/9_0_0/core/org/apache/lucene/analysis/TokenizerFactory.html),
  e.g. [StandardTokenizerFactory](https://lucene.apache.org/core/9_0_0/core/org/apache/lucene/analysis/standard/StandardTokenizerFactory.html)
- [CharFilterFactory](https://lucene.apache.org/core/9_0_0/core/org/apache/lucene/analysis/CharFilterFactory.html),
  e.g. [PatternReplaceCharFilterFactory](https://lucene.apache.org/core/8_1_1/analyzers-common/org/apache/lucene/analysis/pattern/PatternReplaceCharFilterFactory.html)
- [TokenFilterFactory](https://lucene.apache.org/core/8_1_1/analyzers-common/org/apache/lucene/analysis/util/TokenFilterFactory.html),
  e.g. [ReverseStringFilterFactory](https://lucene.apache.org/core/8_1_1/analyzers-common/org/apache/lucene/analysis/reverse/ReverseStringFilterFactory.html)

E.g. tokenizer `StandardTokenizerFactory` has this config [snippet](https://lucene.apache.org/core/9_0_0/core/org/apache/lucene/analysis/standard/StandardTokenizerFactory.html):

```xml
 <fieldType name="text_stndrd" class="solr.TextField" positionIncrementGap="100">
   <analyzer>
     <tokenizer class="solr.StandardTokenizerFactory" maxTokenLength="255"/>
   </analyzer>
 </fieldType>
```

Then go to the <a href="https://github.com/apache/lucene/blob/17c13a76c87c6246f32dd7a78a26db04401ddb6e/lucene/core/src/java/org/apache/lucene/analysis/standard/StandardTokenizerFactory.java#L36" data-proofer-ignore>
source code</a> of the class on GitHub.
Copy value of the `public static final String NAME` into the `<name>` and observe the names used for configuring the tokenizer (in this case only `maxTokenLength`).

```xml
<tokenizer>
  <name>standard</name>
  <config>
    <item key="maxTokenLength">255</item>
  </config>
</tokenizer>
```

The `AnalyzerFactory` constructor on the application startup logs the available analysis components.

The analysis components are discovered through Java Service Provider Interface (SPI).
To add more analysis components it should be enough to put a Lucene analyzer dependency into your application package `pom.xml`
or register services and create classes directly in the application package.

### Resource files

The Lucene analyzers can use various resource files, e.g. for stopwords, synonyms, etc.
The `configDir` configuration parameter controls where to load these files from.
These files are relative to the application package root directory.

If the `configDir` is not specified then files are loaded from the classpath.

## Inspiration

These projects:

- [vespa-chinese-linguistics](https://github.com/vespa-engine/sample-apps/blob/master/examples/vespa-chinese-linguistics/src/main/java/com/qihoo/language/JiebaLinguistics.java)
- [OpenNlp Linguistics](https://github.com/vespa-engine/vespa/blob/50d7555bfe7bdaec86f8b31c4d316c9ba66bb976/opennlp-linguistics/src/main/java/com/yahoo/language/opennlp/OpenNlpLinguistics.java)
- [vespa-kuromoji-linguistics](https://github.com/yahoojapan/vespa-kuromoji-linguistics/tree/main)
- [Clojure library](https://github.com/dainiusjocas/lucene-text-analysis) to work with Lucene analyzers
