# Kotlin Maven Plugin Tools

[![Release](https://github.com/gantsign/kotlin-maven-plugin-tools/workflows/Build/badge.svg)](https://github.com/gantsign/kotlin-maven-plugin-tools/actions?query=workflow%3ABuild)
[![codecov](https://codecov.io/gh/gantsign/kotlin-maven-plugin-tools/branch/main/graph/badge.svg)](https://codecov.io/gh/gantsign/kotlin-maven-plugin-tools)
[![Known Vulnerabilities](https://snyk.io/test/github/gantsign/kotlin-maven-plugin-tools/badge.svg)](https://snyk.io/test/github/gantsign/kotlin-maven-plugin-tools)

Maven plugin metadata extractor for plugins written in Kotlin.

**Note:** this JAR isn't available from Maven Central, see below for how to
configure a `pluginRepository` to download the JAR from GitHub Packages.

**This project is still a little new, so please help by reporting any issues you find.**

## Maven plugin support for Kotlin

The [Java5 annotation metadata extractor](https://maven.apache.org/plugin-tools/maven-plugin-tools-annotations/index.html)
partially works for Kotlin files. It's able to pull all the information from the
annotations; the problem is the descriptions, deprecation messages and since
version information, is still extracted from the JavaDoc, and this doesn't work
for Kotlin files; the generated web site and the command line help are far less
useful without descriptions.

This project provides a Kotlin metadata extractor using the Kotlin compiler to
give the same support for Kotlin files, as the Java5 annotation extractor does
for Java files (including support for descriptions and since version
information from KDoc, and deprecation messages from `@kotlin.Deprecated`).

## Writing a Maven Plugin in Kotlin

Writing a Maven plugin in Kotlin (using this extractor) is much the same as it
normally is in Java; you extend `org.apache.maven.plugin.AbstractMojo`, annotate
the class and properties, and add descriptions in API code comments. However as
Kotlin has nullability constraints in the type system, you need to declare
whether properties can be `null` or not; you can use `lateinit` for the non-null
properties.

It's important that the nullability of the properties matches the value of
the `required` attribute of the
`org.apache.maven.plugins.annotations.Parameter` annotation, or you may get
unfriendly errors at runtime.

```kotlin
package com.example

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter

/**
 * Example description.
 */
@Mojo(name = "example")
class ExampleMojo : AbstractMojo() {

    /**
     * Optional parameter description.
     */
    @Deprecated("Example optional parameter deprecated message.")
    @Parameter(name = "optionalParameter")
    var optionalParameter: String? = null

    /**
     * Required parameter description.
     *
     * @since 1.1
     */
    @Parameter(name = "requiredParameter", required = true)
    lateinit var requiredParameter: String

    override fun execute() {
        // Perform plugin action here
    }
}
```

Your POM will include the following:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.example</groupId>
  <artifactId>example-maven-plugin</artifactId>
  <version>1.0.0-SNAPSHOT</version>

  <packaging>maven-plugin</packaging>

  <prerequisites>
    <maven>${maven.version}</maven>
  </prerequisites>

  <properties>
    <kotlin.version>1.6.21</kotlin.version>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
    <maven.version>3.6.2</maven.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.apache.maven</groupId>
      <artifactId>maven-core</artifactId>
      <version>${maven.version}</version>
    </dependency>
    <dependency>
      <groupId>org.apache.maven</groupId>
      <artifactId>maven-plugin-api</artifactId>
      <version>${maven.version}</version>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-stdlib-jdk8</artifactId>
      <version>${kotlin.version}</version>
    </dependency>
    <dependency>
      <groupId>org.apache.maven.plugin-tools</groupId>
      <artifactId>maven-plugin-annotations</artifactId>
      <version>3.6.0</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-test-junit</artifactId>
      <version>${kotlin.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <sourceDirectory>src/main/kotlin</sourceDirectory>
    <testSourceDirectory>src/test/kotlin</testSourceDirectory>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-plugin-plugin</artifactId>
        <version>3.6.0</version>
        <configuration>
          <extractors>
            <!--
              Use only the Kotlin extractor (also supports annotated Java
              sources)
            -->
            <extractor>kotlin</extractor>
          </extractors>
        </configuration>
        <dependencies>
          <dependency>
            <!-- Add kotlin-maven-plugin-tools`as a dependency to the maven-plugin-plugin -->
            <groupId>com.github.gantsign.maven.plugin-tools</groupId>
            <artifactId>kotlin-maven-plugin-tools</artifactId>
            <!-- Replace @version@ with the latest kotlin-maven-plugin-tools release -->
            <version>@version@</version>
          </dependency>
        </dependencies>
        <executions>
          <execution>
            <id>default-descriptor</id>
            <phase>process-classes</phase>
          </execution>
          <execution>
            <id>help-goal</id>
            <goals>
              <goal>helpmojo</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-maven-plugin</artifactId>
        <version>${kotlin.version}</version>
        <configuration>
          <jvmTarget>1.8</jvmTarget>
        </configuration>
        <executions>
          <execution>
            <id>compile</id>
            <goals>
              <goal>compile</goal>
            </goals>
            <phase>process-sources</phase>
          </execution>
          <execution>
            <id>test-compile</id>
            <goals>
              <goal>test-compile</goal>
            </goals>
            <phase>test-compile</phase>
          </execution>
        </executions>
      </plugin>
    </plugins>

  </build>

  <!--
    kotlin-maven-plugin-tools isn't available from Maven Central, so you
    have to add the following plugin repository.
    Where possible you you should use a repository manager rather than adding
    the repository directly to your POM.
  -->
  <pluginRepositories>
      <pluginRepository>
          <!-- id must match the id of the server in settings.xml -->
          <id>github</id>
          <name>GitHub Packages</name>
          <url>https://maven.pkg.github.com/gantsign/kotlin-maven-plugin-tools</url>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
      </pluginRepository>
  </pluginRepositories>

</project>
```

GitHub Packages repositories require authentication, so you need to specify the
credentials in your `settings.xml`. You don't need particular permissions, any
valid GitHub account token will do.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns="http://maven.apache.org/SETTINGS/1.0.0"
    xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                          https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <!-- This is setup for you if using id "github" with
           https://github.com/actions/setup-java -->
      <!-- id must match the id of the pluginRepository -->
      <id>github</id>
      <username>${env.GITHUB_ACTOR}</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>

</settings>
```

If using GitHub Actions, you also need to pass the `GITHUB_TOKEN` environment
variable to the build step(s) in your workflow file e.g.:

```yaml
- name: Build with Maven
  run: mvn install
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

License
-------

This software is licensed under the terms in the file named "LICENSE" in the
root directory of this project. This project has dependencies that are under
different licenses.

Author Information
------------------

John Freeman

GantSign Ltd.
Company No. 06109112 (registered in England)
