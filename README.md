# Kotlin Maven Plugin Tools

[![Build Status](https://travis-ci.org/gantsign/kotlin-maven-plugin-tools.svg?branch=master)](https://travis-ci.org/gantsign/kotlin-maven-plugin-tools)
[![codecov](https://codecov.io/gh/gantsign/kotlin-maven-plugin-tools/branch/master/graph/badge.svg)](https://codecov.io/gh/gantsign/kotlin-maven-plugin-tools)

Maven plugin metadata extractor for plugins written in Kotlin.

**This project is still a little new, so please help by reporting any issues you find.**

## Maven plugin support for Kotlin

The [Java5 annotation metadata extractor](https://maven.apache.org/plugin-tools/maven-plugin-tools-annotations/index.html)
partially works for Kotlin files. It's able to pull all the information from the
annotations; the problem is the descriptions, deprecation messages and since
version information, is still extracted from the JavaDoc, and this doesn't work
for Kotlin files; the generated web site and the command line help are far less
useful without descriptions.

This project provides a Kotlin metadata extractor using
[Dokka](https://github.com/Kotlin/dokka) to give the same support for Kotlin
files, as the Java5 annotation extractor does for Java files (including support
for descriptions and since version information from KDoc, and deprecation
messages from `@kotlin.Deprecated`).

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
    <kotlin.version>1.2.41</kotlin.version>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
    <maven.version>3.5.3</maven.version>
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
      <version>3.5.1</version>
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
        <version>3.5.1</version>
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
          <id>bintray-gantsign-maven</id>
          <name>bintray-plugins</name>
          <url>https://dl.bintray.com/gantsign/maven</url>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
      </pluginRepository>
  </pluginRepositories>

</project>
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
