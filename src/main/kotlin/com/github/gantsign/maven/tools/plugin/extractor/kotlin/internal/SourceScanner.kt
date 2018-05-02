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
package com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal

import com.thoughtworks.qdox.JavaProjectBuilder
import com.thoughtworks.qdox.library.SortedClassLibraryBuilder
import com.thoughtworks.qdox.model.JavaClass
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.resolver.ArtifactNotFoundException
import org.apache.maven.artifact.resolver.ArtifactResolutionException
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest
import org.apache.maven.project.MavenProject
import org.apache.maven.repository.RepositorySystem
import org.apache.maven.tools.plugin.PluginToolsRequest
import org.apache.maven.tools.plugin.extractor.ExtractionException
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotatedClass
import org.codehaus.plexus.archiver.manager.ArchiverManager
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException
import org.codehaus.plexus.logging.Logger
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal class SourceScanner(
    private val logger: Logger,
    private val repositorySystem: RepositorySystem,
    private val archiverManager: ArchiverManager
) {

    fun scanJavadoc(
        request: PluginToolsRequest,
        mojoAnnotatedClasses: Collection<MojoAnnotatedClass>
    ): Map<String, JavaClass> {
        val requestProject = request.project!!
        val requestArtifact = requestProject.artifact!!

        val mavenProjects = mutableListOf<MavenProject>()
        val externalArtifacts = mutableSetOf<Artifact>()
        for (mojoAnnotatedClass in mojoAnnotatedClasses) {
            val classArtifact = mojoAnnotatedClass.artifact!!
            if (classArtifact.artifactId == requestArtifact.artifactId) {
                continue
            }

            if (!mojoAnnotatedClass.isCandidate()) {
                // we don't scan sources for classes without mojo annotations
                continue
            }

            classArtifact.fromProjectReferences(requestProject)
                ?.also { mavenProjects.add(it) }
                ?: externalArtifacts.add(classArtifact)
        }

        val classMapFromExternalSources: Map<String, JavaClass> = externalArtifacts
            .map { artifact ->
                val isTestSources = "tests".equals(artifact.classifier, ignoreCase = true)
                val classifier = if (isTestSources) "test-sources" else "sources"

                artifact.discoverClassesFromSourcesJar(request, classifier)
            }
            .fold(mutableMapOf()) { acc, element -> acc.putAll(element); acc }

        val classMapFromReactorSources: Map<String, JavaClass> = mavenProjects
            .map { it.discoverClasses(request.encoding) }
            .fold(mutableMapOf()) { acc, element -> acc.putAll(element); acc }

        val classMapFromLocalSources = request.discoverClasses()

        return classMapFromExternalSources + classMapFromReactorSources + classMapFromLocalSources
    }

    private fun MojoAnnotatedClass?.isCandidate(): Boolean =
        this != null && hasAnnotations()

    private fun Artifact.discoverClassesFromSourcesJar(
        request: PluginToolsRequest,
        classifier: String
    ): Map<String, JavaClass> {
        val artifact = this

        try {
            val sourcesArtifact =
                repositorySystem.createArtifactWithClassifier(
                    artifact.groupId!!,
                    artifact.artifactId!!,
                    artifact.version!!,
                    artifact.type!!,
                    classifier
                )!!

            repositorySystem.resolve(ArtifactResolutionRequest().apply {
                this.artifact = sourcesArtifact
                localRepository = request.local
                remoteRepositories = request.remoteRepos
            })

            val sourcesArtifactFile = sourcesArtifact.file
                ?.takeIf(File::exists)
                ?: return emptyMap() // could not get artifact sources

            // extract sources to target/maven-plugin-plugin-sources/${groupId}/${artifact}/${version}/sources
            val extractDirectory = sourcesArtifact.let {
                Paths.get(
                    request.project.build.directory!!,
                    "maven-plugin-plugin-sources",
                    it.groupId!!,
                    it.artifactId!!,
                    it.version!!,
                    classifier
                )!!
            }
            try {
                Files.createDirectories(extractDirectory)
            } catch (e: IOException) {
                throw ExtractionException(e.message, e)
            }

            archiverManager.getUnArchiver("jar").apply {
                sourceFile = sourcesArtifactFile
                destDirectory = extractDirectory.toFile()
                extract()
            }

            return extractDirectory.discoverClasses(request)
        } catch (e: ArtifactResolutionException) {
            throw ExtractionException(e.message, e)
        } catch (e: ArtifactNotFoundException) {
            logger.debug("skip ArtifactNotFoundException: ${e.message}")
            logger.warn("Unable to get sources artifact for ${artifact.groupId}:${artifact.artifactId}:${artifact.version}; javadoc tags (@since, @deprecated and comments) won't be available.")
            return emptyMap()
        } catch (e: NoSuchArchiverException) {
            throw ExtractionException(e.message, e)
        }
    }

    private fun PluginToolsRequest.discoverClasses(): Map<String, JavaClass> =
        project.discoverClasses(encoding)

    private fun MavenProject.discoverClasses(encoding: String): Map<String, JavaClass> {
        val compileSourceRoots: List<String> = compileSourceRoots!!
        var sources = compileSourceRoots.map { Paths.get(it)!! }

        val generatedPlugin = basedir.toPath()
            .resolve(Paths.get("target", "generated-sources", "plugin"))
            .toAbsolutePath()!!

        if (!compileSourceRoots.contains(generatedPlugin.toString())
            && Files.exists(generatedPlugin)
        ) {
            sources += generatedPlugin
        }

        return sources.discoverClasses(encoding, artifacts)
    }

    private fun Path.discoverClasses(
        request: PluginToolsRequest
    ): Map<String, JavaClass> =
        listOf(this)
            .discoverClasses(request.encoding!!, request.dependencies!!)

    private fun List<Path>.discoverClasses(
        encoding: String,
        artifacts: Set<Artifact>
    ): Map<String, JavaClass> {
        val sourceDirectories = this

        // Build isolated Classloader with only the artifacts of the project (none of this plugin)
        val classLoader = URLClassLoader(
            artifacts.asSequence()
                .mapNotNull({
                    try {
                        it.file.toURI().toURL()!!
                    } catch (e: MalformedURLException) {
                        null
                    }
                })
                .toList()
                .toTypedArray(),
            ClassLoader.getSystemClassLoader()
        )

        val builder = JavaProjectBuilder(SortedClassLibraryBuilder()).apply {
            setEncoding(encoding)
            addClassLoader(classLoader)

            for (dir in sourceDirectories) {
                addSourceTree(dir.toFile())
            }
        }

        val javaClasses: MutableCollection<JavaClass> = builder.classes ?: return emptyMap()

        return javaClasses.associateBy { it.fullyQualifiedName!! }
    }

    private fun Artifact.fromProjectReferences(project: MavenProject): MavenProject? {
        val mavenProjects: Collection<MavenProject>? = project.projectReferences?.values

        return mavenProjects?.firstOrNull { it.id == id }
    }
}
