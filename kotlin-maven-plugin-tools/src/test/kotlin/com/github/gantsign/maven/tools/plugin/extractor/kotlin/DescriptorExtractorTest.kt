/*-
 * #%L
 * kotlin-maven-plugin-tools
 * %%
 * Copyright (C) 2018 GantSign Ltd.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.gantsign.maven.tools.plugin.extractor.kotlin

import java.io.File
import kotlin.test.assertNotNull
import org.apache.maven.model.Build
import org.apache.maven.plugin.descriptor.MojoDescriptor
import org.apache.maven.plugin.descriptor.Parameter
import org.apache.maven.plugin.descriptor.PluginDescriptor
import org.apache.maven.plugin.testing.MojoRule
import org.apache.maven.plugin.testing.stubs.MavenProjectStub
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.repository.RepositorySystem
import org.apache.maven.tools.plugin.DefaultPluginToolsRequest
import org.apache.maven.tools.plugin.extractor.MojoDescriptorExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

class DescriptorExtractorTest {

    @Rule
    @JvmField
    val mojoRule = MojoRule()

    private val expectedPluginDescriptor = PluginDescriptor()

    private fun parseMojos(): List<MojoDescriptor?> {
        val descriptorExtractor =
            assertNotNull(mojoRule.container.lookup(MojoDescriptorExtractor::class.java, "kotlin"))

        mojoRule.readMavenProject(File("src/test/resources/"))!!

        val repositorySystem = mojoRule.lookup<RepositorySystem>(RepositorySystem::class.java)!!

        val localRepository = repositorySystem.createDefaultLocalRepository()!!
        val remoteRepositories = listOf(repositorySystem.createDefaultRemoteRepository()!!)

        val jarPluginArtifact = localRepository.find(
            assertNotNull(
                repositorySystem.createArtifact(
                    "org.apache.maven.plugins",
                    "maven-jar-plugin",
                    "3.2.2",
                    "jar"
                )
            )
        )!!

        val project = MavenProjectStub().apply {
            build = Build().apply {
                directory = "target"
                outputDirectory = "target/test-classes"
            }

            artifact = repositorySystem.createArtifact(
                "test.groupId",
                "test.artifactId",
                "1.0",
                "jar"
            )

            artifactId = "test"

            addCompileSourceRoot("src/test/java")
            addCompileSourceRoot("src/test/kotlin")
        }

        val pluginToolsRequest = DefaultPluginToolsRequest(
            project,
            expectedPluginDescriptor
        ).apply {
            local = localRepository
            remoteRepos = remoteRepositories
            dependencies = setOf(jarPluginArtifact)
        }

        val mojoAnnotatedClasses: List<MojoDescriptor?> =
            assertNotNull(descriptorExtractor.execute(pluginToolsRequest))

        assertThat(mojoAnnotatedClasses).isNotEmpty.hasSize(6)
        return mojoAnnotatedClasses
    }

    @Test
    fun readMinimalMojo() {
        val mojoAnnotatedClasses: List<MojoDescriptor?> = parseMojos()

        assertNotNull(
            mojoAnnotatedClasses.firstOrNull { it?.goal == "minimal" },
            message = "Expected 'minimal' mojo"
        ).apply {
            assertThat(parameters).isNull()
            assertThat(parameterMap).isEmpty()
            assertThat(executionStrategy).isEqualTo(MojoDescriptor.SINGLE_PASS_EXEC_STRATEGY)
            assertThat(phase).isEmpty()
            assertThat(since).isNull()
            assertThat(executePhase).isNull()
            assertThat(executeGoal).isNull()
            assertThat(executeLifecycle).isNull()
            assertThat(deprecated).isNull()
            assertThat(isAggregator).isFalse()
            assertThat(dependencyResolutionRequired).isNull()
            assertThat(dependencyCollectionRequired).isNull()
            assertThat(isProjectRequired).isTrue()
            assertThat(isOnlineRequired).isFalse()
            assertThat(pluginDescriptor).isSameAs(expectedPluginDescriptor)
            assertThat(isInheritedByDefault).isTrue()
            assertThat(isDirectInvocationOnly).isFalse()
            assertThat(isRequiresReports).isFalse()
            assertThat(isThreadSafe).isFalse()
        }
    }

    @Test
    fun readLocalMojo() {
        val mojoAnnotatedClasses: List<MojoDescriptor?> = parseMojos()

        val mojoDescriptor: MojoDescriptor = assertNotNull(
            mojoAnnotatedClasses.firstOrNull { it?.goal == "local" },
            message = "Expected 'local' mojo"
        ).apply {
            assertThat(executionStrategy).isEqualTo(MojoDescriptor.MULTI_PASS_EXEC_STRATEGY)
            assertThat(phase).isEqualTo(LifecyclePhase.COMPILE.id())
            assertThat(since).isEqualTo("localVersion")
            assertThat(executePhase).isEqualTo(LifecyclePhase.PACKAGE.id())
            assertThat(executeGoal).isEqualTo("compiler")
            assertThat(executeLifecycle).isEqualTo("localLifecycle")
            assertThat(deprecated).isEqualTo("Local deprecated.")
            assertThat(isAggregator).isTrue()
            assertThat(dependencyResolutionRequired).isEqualTo(ResolutionScope.COMPILE.id())
            assertThat(dependencyCollectionRequired).isEqualTo(ResolutionScope.RUNTIME.id())
            assertThat(isProjectRequired).isFalse()
            assertThat(isOnlineRequired).isTrue()
            assertThat(pluginDescriptor).isSameAs(expectedPluginDescriptor)
            assertThat(isInheritedByDefault).isFalse()
            assertThat(isDirectInvocationOnly).isTrue()
            assertThat(isRequiresReports).isTrue()
            assertThat(isThreadSafe).isTrue()
        }

        val parameters: List<Parameter?> = assertNotNull(mojoDescriptor.parameters)
        val parameterMap: Map<String, Parameter> = assertNotNull(mojoDescriptor.parameterMap)
        assertThat(parameters).isNotEmpty.hasSize(5)

        assertThat(parameterMap)
            .isNotEmpty
            .hasSize(5)
            .isEqualTo(parameters.associateBy { it?.name })

        assertNotNull(
            parameterMap["minimalParameter"],
            message = "Expected parameter with name 'minimalParameter'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("minimalParameter")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isTrue()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["everythingParameterName"],
            message = "Expected parameter with name 'everythingParameterName'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("everythingParameterName")
            assertThat(isRequired).isTrue()
            assertThat(description).isEqualTo("Everything parameter description")
            assertThat(expression).isEqualTo("\${everythingParameterProperty}")
            assertThat(deprecated).isEqualTo("everything parameter deprecated message")
            assertThat(alias).isEqualTo("everythingParameterAlias")
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isEqualTo("everythingParameterDefaultValue")
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isEqualTo("everythingParameterVersion")
        }

        assertNotNull(
            parameterMap["minimalComponent"],
            message = "Expected parameter with name 'minimalComponent'"
        ).apply {
            assertThat(type).isNull()
            assertThat(name).isEqualTo("minimalComponent")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isNull()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNotNull
            assertThat(requirement.role).isEqualTo("org.apache.maven.repository.RepositorySystem")
            assertThat(requirement.roleHint).isEmpty()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["everythingComponent"],
            message = "Expected parameter with name 'everythingComponent'"
        ).apply {
            assertThat(type).isNull()
            assertThat(name).isEqualTo("everythingComponent")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isNull()
            assertThat(deprecated).isEqualTo("everything component deprecated message")
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNotNull
            assertThat(requirement.role).isEqualTo("org.codehaus.plexus.compiler.manager.CompilerManager")
            assertThat(requirement.roleHint).isEqualTo("everythingComponentHint")
            assertThat(implementation).isNull()
            assertThat(since).isEqualTo("everythingComponentVersion")
        }

        assertNotNull(
            parameterMap["componentAsParameter"],
            message = "Expected parameter with name 'componentAsParameter'"
        ).apply {
            assertThat(type).isEqualTo("org.apache.maven.plugin.MojoExecution")
            assertThat(name).isEqualTo("componentAsParameter")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isEqualTo("\${mojoExecution}")
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }
    }

    @Test
    fun readExtendsLocalMojo() {
        val mojoAnnotatedClasses: List<MojoDescriptor?> = parseMojos()

        val mojoDescriptor: MojoDescriptor = assertNotNull(
            mojoAnnotatedClasses.firstOrNull { it?.goal == "extendslocal" },
            message = "Expected 'extendslocal' mojo"
        ).apply {
            assertThat(executionStrategy).isEqualTo(MojoDescriptor.MULTI_PASS_EXEC_STRATEGY)
            assertThat(phase).isEqualTo(LifecyclePhase.COMPILE.id())
            assertThat(since).isEqualTo("localVersion")
            assertThat(executePhase).isEqualTo(LifecyclePhase.PACKAGE.id())
            assertThat(executeGoal).isNull()
            assertThat(executeLifecycle).isEqualTo("extendsLocalLifecycle")
            assertThat(deprecated).isEqualTo("Extends local deprecated.")
            assertThat(isAggregator).isFalse()
            assertThat(dependencyResolutionRequired).isEqualTo(ResolutionScope.COMPILE_PLUS_RUNTIME.id())
            assertThat(dependencyCollectionRequired).isEqualTo(ResolutionScope.RUNTIME.id())
            assertThat(isProjectRequired).isFalse()
            assertThat(isOnlineRequired).isTrue()
            assertThat(pluginDescriptor).isSameAs(expectedPluginDescriptor)
            assertThat(isInheritedByDefault).isFalse()
            assertThat(isDirectInvocationOnly).isTrue()
            assertThat(isRequiresReports).isFalse()
            assertThat(isThreadSafe).isTrue()
        }

        val parameters: List<Parameter?> = assertNotNull(mojoDescriptor.parameters)
        val parameterMap: Map<String, Parameter> = assertNotNull(mojoDescriptor.parameterMap)
        assertThat(parameters).isNotEmpty.hasSize(6)

        assertThat(parameterMap)
            .isNotEmpty
            .hasSize(6)
            .isEqualTo(parameters.associateBy { it?.name })

        assertNotNull(
            parameterMap["minimalParameter"],
            message = "Expected parameter with name 'minimalParameter'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("minimalParameter")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isTrue()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["everythingParameterName"],
            message = "Expected parameter with name 'everythingParameterName'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("everythingParameterName")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isEqualTo("extends everything parameter deprecated message")
            assertThat(alias).isEqualTo("extendsEverythingParameterAlias")
            assertThat(isEditable).isTrue()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["minimalComponent"],
            message = "Expected parameter with name 'minimalComponent'"
        ).apply {
            assertThat(type).isNull()
            assertThat(name).isEqualTo("minimalComponent")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isNull()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNotNull
            assertThat(requirement.role).isEqualTo("org.apache.maven.repository.RepositorySystem")
            assertThat(requirement.roleHint).isEmpty()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["everythingComponent"],
            message = "Expected parameter with name 'everythingComponent'"
        ).apply {
            assertThat(type).isNull()
            assertThat(name).isEqualTo("everythingComponent")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isNull()
            assertThat(deprecated).isEqualTo("extends everything component deprecated message")
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNotNull
            assertThat(requirement.role).isEqualTo("org.codehaus.plexus.compiler.manager.CompilerManager")
            assertThat(requirement.roleHint).isEqualTo("extendsEverythingComponentHint")
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["componentAsParameter"],
            message = "Expected parameter with name 'componentAsParameter'"
        ).apply {
            assertThat(type).isEqualTo("org.apache.maven.plugin.MojoExecution")
            assertThat(name).isEqualTo("componentAsParameter")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isEqualTo("\${mojoExecution}")
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["additionalParameter"],
            message = "Expected parameter with name 'additionalParameter'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("additionalParameter")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isTrue()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }
    }

    @Test
    fun readExtendsExternalMojo() {
        val mojoAnnotatedClasses: List<MojoDescriptor?> = parseMojos()

        val mojoDescriptor: MojoDescriptor = assertNotNull(
            mojoAnnotatedClasses.firstOrNull { it?.goal == "extendsexternal" },
            message = "Expected 'extendsexternal' mojo"
        ).apply {
            assertThat(executionStrategy).isEqualTo(MojoDescriptor.MULTI_PASS_EXEC_STRATEGY)
            assertThat(phase).isEqualTo(LifecyclePhase.COMPILE.id())
            assertThat(since).isNull()
            assertThat(executePhase).isEqualTo(LifecyclePhase.PACKAGE.id())
            assertThat(executeGoal).isNull()
            assertThat(executeLifecycle).isEqualTo("extendsExternalLifecycle")
            assertThat(deprecated).isEqualTo("Extends external deprecated.")
            assertThat(isAggregator).isFalse()
            assertThat(dependencyResolutionRequired).isEqualTo(ResolutionScope.COMPILE_PLUS_RUNTIME.id())
            assertThat(dependencyCollectionRequired).isEqualTo(ResolutionScope.RUNTIME.id())
            assertThat(isProjectRequired).isFalse()
            assertThat(isOnlineRequired).isTrue()
            assertThat(pluginDescriptor).isSameAs(expectedPluginDescriptor)
            assertThat(isInheritedByDefault).isFalse()
            assertThat(isDirectInvocationOnly).isTrue()
            assertThat(isRequiresReports).isFalse()
            assertThat(isThreadSafe).isTrue()
        }

        val parameters: List<Parameter?> = assertNotNull(mojoDescriptor.parameters)
        val parameterMap: Map<String, Parameter> = assertNotNull(mojoDescriptor.parameterMap)
        assertThat(parameters).isNotEmpty

        assertThat(parameterMap)
            .isNotEmpty
            .isEqualTo(parameters.associateBy { it?.name })

        assertNotNull(
            parameterMap["finalName"],
            message = "Expected parameter with name 'finalName'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("finalName")
            assertThat(isRequired).isFalse()
            assertThat(description).isEqualTo("Name of the generated JAR.")
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isEqualTo("\${project.build.finalName}")
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["additionalParameter"],
            message = "Expected parameter with name 'additionalParameter'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("additionalParameter")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isTrue()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }
    }

    @Test
    fun readKotlinMojo() {
        val mojoAnnotatedClasses: List<MojoDescriptor?> = parseMojos()

        val mojoDescriptor: MojoDescriptor = assertNotNull(
            mojoAnnotatedClasses.firstOrNull { it?.goal == "kotlin" },
            message = "Expected 'kotlin' mojo"
        ).apply {
            assertThat(executionStrategy).isEqualTo(MojoDescriptor.MULTI_PASS_EXEC_STRATEGY)
            assertThat(phase).isEqualTo(LifecyclePhase.COMPILE.id())
            assertThat(since).isEqualTo("kotlinLocalVersion")
            assertThat(executePhase).isEqualTo(LifecyclePhase.PACKAGE.id())
            assertThat(executeGoal).isEqualTo("compiler")
            assertThat(executeLifecycle).isEqualTo("kotlinLocalLifecycle")
            assertThat(deprecated).isEqualTo("Kotlin local deprecated.")
            assertThat(isAggregator).isTrue()
            assertThat(dependencyResolutionRequired).isEqualTo(ResolutionScope.COMPILE.id())
            assertThat(dependencyCollectionRequired).isEqualTo(ResolutionScope.RUNTIME.id())
            assertThat(isProjectRequired).isFalse()
            assertThat(isOnlineRequired).isTrue()
            assertThat(pluginDescriptor).isSameAs(expectedPluginDescriptor)
            assertThat(isInheritedByDefault).isFalse()
            assertThat(isDirectInvocationOnly).isTrue()
            assertThat(isRequiresReports).isTrue()
            assertThat(isThreadSafe).isTrue()
        }

        val parameters: List<Parameter?> = assertNotNull(mojoDescriptor.parameters)
        val parameterMap: Map<String, Parameter> = assertNotNull(mojoDescriptor.parameterMap)
        assertThat(parameters).isNotEmpty.hasSize(5)

        assertThat(parameterMap)
            .isNotEmpty
            .hasSize(5)
            .isEqualTo(parameters.associateBy { it?.name })

        assertNotNull(
            parameterMap["minimalParameter"],
            message = "Expected parameter with name 'minimalParameter'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("minimalParameter")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isTrue()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["everythingParameterName"],
            message = "Expected parameter with name 'everythingParameterName'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("everythingParameterName")
            assertThat(isRequired).isTrue()
            assertThat(description).isEqualTo(
                """
                Everything parameter description.<br />
                Paragraph 2.<br />
                This is a <a href="https://example.com">link</a>.<br />
                This is <em>text with emphasis</em>.<br />
                This is <strong>strong text</strong>.<br />
                This is <code>inline code</code>.
                """.trimIndent()
            )
            assertThat(expression).isEqualTo("\${everythingParameterProperty}")
            assertThat(deprecated).isEqualTo("everything parameter deprecated message")
            assertThat(alias).isEqualTo("everythingParameterAlias")
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isEqualTo("everythingParameterDefaultValue")
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isEqualTo("everythingParameterVersion")
        }

        assertNotNull(
            parameterMap["minimalComponent"],
            message = "Expected parameter with name 'minimalComponent'"
        ).apply {
            assertThat(type).isNull()
            assertThat(name).isEqualTo("minimalComponent")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isNull()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNotNull
            assertThat(requirement.role).isEqualTo("org.apache.maven.repository.RepositorySystem")
            assertThat(requirement.roleHint).isEmpty()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["everythingComponent"],
            message = "Expected parameter with name 'everythingComponent'"
        ).apply {
            assertThat(type).isNull()
            assertThat(name).isEqualTo("everythingComponent")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isNull()
            assertThat(deprecated).isEqualTo("everything component deprecated message")
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNotNull
            assertThat(requirement.role).isEqualTo("org.codehaus.plexus.compiler.manager.CompilerManager")
            assertThat(requirement.roleHint).isEqualTo("everythingComponentHint")
            assertThat(implementation).isNull()
            assertThat(since).isEqualTo("everythingComponentVersion")
        }

        assertNotNull(
            parameterMap["componentAsParameter"],
            message = "Expected parameter with name 'componentAsParameter'"
        ).apply {
            assertThat(type).isEqualTo("org.apache.maven.plugin.MojoExecution")
            assertThat(name).isEqualTo("componentAsParameter")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isEqualTo("\${mojoExecution}")
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }
    }

    @Test
    fun readKotlinExtendsLocalMojo() {
        val mojoAnnotatedClasses: List<MojoDescriptor?> = parseMojos()

        val mojoDescriptor: MojoDescriptor = assertNotNull(
            mojoAnnotatedClasses.firstOrNull { it?.goal == "kotlinextendslocal" },
            message = "Expected 'kotlinextendslocal' mojo"
        ).apply {
            assertThat(executionStrategy).isEqualTo(MojoDescriptor.MULTI_PASS_EXEC_STRATEGY)
            assertThat(phase).isEqualTo(LifecyclePhase.COMPILE.id())
            assertThat(since).isEqualTo("kotlinExtendsLocalVersion")
            assertThat(executePhase).isEqualTo(LifecyclePhase.PACKAGE.id())
            assertThat(executeGoal).isNull()
            assertThat(executeLifecycle).isEqualTo("kotlinExtendsLocalLifecycle")
            assertThat(deprecated).isEqualTo("Kotlin extends local deprecated.")
            assertThat(isAggregator).isFalse()
            assertThat(dependencyResolutionRequired).isEqualTo(ResolutionScope.COMPILE_PLUS_RUNTIME.id())
            assertThat(dependencyCollectionRequired).isEqualTo(ResolutionScope.RUNTIME.id())
            assertThat(isProjectRequired).isFalse()
            assertThat(isOnlineRequired).isTrue()
            assertThat(pluginDescriptor).isSameAs(expectedPluginDescriptor)
            assertThat(isInheritedByDefault).isFalse()
            assertThat(isDirectInvocationOnly).isTrue()
            assertThat(isRequiresReports).isFalse()
            assertThat(isThreadSafe).isTrue()
        }

        val parameters: List<Parameter?> = assertNotNull(mojoDescriptor.parameters)
        val parameterMap: Map<String, Parameter> = assertNotNull(mojoDescriptor.parameterMap)
        assertThat(parameters).isNotEmpty.hasSize(6)

        assertThat(parameterMap)
            .isNotEmpty
            .hasSize(6)
            .isEqualTo(parameters.associateBy { it?.name })

        assertNotNull(
            parameterMap["minimalParameter"],
            message = "Expected parameter with name 'minimalParameter'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("minimalParameter")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isTrue()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["everythingParameterName"],
            message = "Expected parameter with name 'everythingParameterName'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("everythingParameterName")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isEqualTo("extends everything parameter deprecated message")
            assertThat(alias).isEqualTo("extendsEverythingParameterAlias")
            assertThat(isEditable).isTrue()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["minimalComponent"],
            message = "Expected parameter with name 'minimalComponent'"
        ).apply {
            assertThat(type).isNull()
            assertThat(name).isEqualTo("minimalComponent")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isNull()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNotNull
            assertThat(requirement.role).isEqualTo("org.apache.maven.repository.RepositorySystem")
            assertThat(requirement.roleHint).isEmpty()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["everythingComponent"],
            message = "Expected parameter with name 'everythingComponent'"
        ).apply {
            assertThat(type).isNull()
            assertThat(name).isEqualTo("everythingComponent")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isNull()
            assertThat(deprecated).isEqualTo("extends everything component deprecated message")
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNotNull
            assertThat(requirement.role).isEqualTo("org.codehaus.plexus.compiler.manager.CompilerManager")
            assertThat(requirement.roleHint).isEqualTo("extendsEverythingComponentHint")
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["componentAsParameter"],
            message = "Expected parameter with name 'componentAsParameter'"
        ).apply {
            assertThat(type).isEqualTo("org.apache.maven.plugin.MojoExecution")
            assertThat(name).isEqualTo("componentAsParameter")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isFalse()
            assertThat(defaultValue).isEqualTo("\${mojoExecution}")
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }

        assertNotNull(
            parameterMap["additionalParameter"],
            message = "Expected parameter with name 'additionalParameter'"
        ).apply {
            assertThat(type).isEqualTo("java.lang.String")
            assertThat(name).isEqualTo("additionalParameter")
            assertThat(isRequired).isFalse()
            assertThat(description).isNull()
            assertThat(expression).isEmpty()
            assertThat(deprecated).isNull()
            assertThat(alias).isNull()
            assertThat(isEditable).isTrue()
            assertThat(defaultValue).isNull()
            assertThat(requirement).isNull()
            assertThat(implementation).isNull()
            assertThat(since).isNull()
        }
    }
}
