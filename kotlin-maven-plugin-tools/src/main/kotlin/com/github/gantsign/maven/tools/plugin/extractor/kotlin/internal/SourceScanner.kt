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

import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.dokka.DokkaSourceScanner
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.ClassDoc
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.SourceScanRequest
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.SourceScanRequest.ArtifactScanRequest
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.SourceScanRequest.ProjectScanRequest
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.qdox.QDoxSourceScanner
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
import java.nio.file.Files
import java.nio.file.Paths

internal class SourceScanner(
    private val logger: Logger,
    private val repositorySystem: RepositorySystem,
    private val archiverManager: ArchiverManager
) {

    fun scanSourceDoc(
        request: PluginToolsRequest,
        mojoAnnotatedClasses: Collection<MojoAnnotatedClass>
    ): Map<String, ClassDoc> {
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

        val scanRequestsForExternalSources: List<SourceScanRequest> =
            externalArtifacts.mapNotNull { artifact ->
                val isTestSources = "tests".equals(artifact.classifier, ignoreCase = true)
                val classifier = if (isTestSources) "test-sources" else "sources"

                artifact.createScanRequest(request, classifier)
            }

        val scanRequestsForReactorSources: List<ProjectScanRequest> =
            mavenProjects.map { it.createScanRequest() }

        val scanRequestsForLocalSources = request.project.createScanRequest()

        val sourceScanRequests =
            scanRequestsForExternalSources + scanRequestsForReactorSources + scanRequestsForLocalSources

        val dokkaSourceScanner = DokkaSourceScanner(logger, request)
        val dokkaClassDoc = dokkaSourceScanner.scanSourceDoc(sourceScanRequests)

        val qDoxSourceScanner = QDoxSourceScanner(logger, request)
        val qDoxClassDoc = qDoxSourceScanner.scanSourceDoc(sourceScanRequests)

        // The Dokka scanner omits fields for Java classes so we overwrite the Dokka data for
        // Java classes with the data from QDox
        return dokkaClassDoc + qDoxClassDoc
    }

    private fun MojoAnnotatedClass.isCandidate(): Boolean = hasAnnotations()

    private fun Artifact.createScanRequest(
        request: PluginToolsRequest,
        classifier: String
    ): SourceScanRequest? {
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
                ?: return null // could not get artifact sources

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

            return ArtifactScanRequest(artifact, extractDirectory)
        } catch (e: ArtifactResolutionException) {
            throw ExtractionException(e.message, e)
        } catch (e: ArtifactNotFoundException) {
            logger.debug("skip ArtifactNotFoundException: ${e.message}")
            logger.warn("Unable to get sources artifact for ${artifact.groupId}:${artifact.artifactId}:${artifact.version}; javadoc tags (@since, @deprecated and comments) won't be available.")
            return null
        } catch (e: NoSuchArchiverException) {
            throw ExtractionException(e.message, e)
        }
    }

    private fun MavenProject.createScanRequest(): ProjectScanRequest {
        val compileSourceRoots: List<String> = compileSourceRoots!!
        var sources = compileSourceRoots.map { Paths.get(it)!! }

        val generatedPlugin = basedir.toPath()
            .resolve(Paths.get("target", "generated-sources", "plugin"))
            .toAbsolutePath()!!

        if (generatedPlugin.toString() !in compileSourceRoots
            && Files.exists(generatedPlugin)
        ) {
            sources += generatedPlugin
        }

        return ProjectScanRequest(this, sources)
    }

    private fun Artifact.fromProjectReferences(project: MavenProject): MavenProject? {
        val mavenProjects: Collection<MavenProject>? = project.projectReferences?.values

        return mavenProjects?.firstOrNull { it.id == id }
    }
}
