<!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
Vespa Config Generation
=======================

The vespa-configgen plugin is used to generate config-classes from .def files.

Userguide
---------
Put your .def files in `src/main/vespa-configdef`

Depend on this plugin in your `pom.xml`

    <dependencies>
      <dependency>
        <groupId>com.yahoo.vespa</groupId>
        <artifactId>vespa-configgen-plugin</artifactId>
        <version>1.0-SNAPSHOT</version>
      </dependency>
    </dependencies>

Add the following to the 'build' section of your `pom.xml`

    <build>
      <plugins>
        <plugin>
          <groupId>com.yahoo.vespa</groupId>
          <artifactId>vespa-configgen-plugin</artifactId>
          <executions>
            <execution>
              <id>config-gen</id>
              <goals>
                <goal>config-gen</goal>
              </goals>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </build>

The .def files will now be processed during the generate-sources step, and produce output
in the `target/generated-sources/vespa-configgen-plugin` directory. This directory
is automatically added to the source path of your project.

It is possible to configure the location(s) of def-files, and the output of the generated sources.
Put the following configuration under the plugin in the build section:

    <build>
      <plugins>
        <plugin>
          <groupId>com.yahoo.vespa</groupId>
          <artifactId>vespa-configgen-plugin</artifactId>
          <configuration>
            <defFilesDirectories>etc, src/main/def-files</defFilesDirectories>
            <outputDirectory>target/generated-sources/vespa-configgen-plugin</outputDirectory>
          </configuration>
        </plugin>
      </plugins>
    </build>

To run only the config-gen goal from the command-line:
$ mvn com.yahoo.vespa:vespa-configgen-plugin:config-gen
