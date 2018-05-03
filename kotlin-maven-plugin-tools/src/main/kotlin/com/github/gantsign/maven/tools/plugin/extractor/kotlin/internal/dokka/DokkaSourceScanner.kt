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
package com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.dokka

import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.ClassDoc
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.SourceScanRequest
import com.google.inject.Guice
import com.intellij.openapi.util.Disposer
import org.apache.maven.artifact.Artifact
import org.apache.maven.tools.plugin.PluginToolsRequest
import org.codehaus.plexus.logging.Logger
import org.jetbrains.dokka.AnalysisEnvironment
import org.jetbrains.dokka.DefaultPlatformsProvider
import org.jetbrains.dokka.DocumentationModule
import org.jetbrains.dokka.DocumentationOptions
import org.jetbrains.dokka.DokkaLogger
import org.jetbrains.dokka.DokkaMessageCollector
import org.jetbrains.dokka.Utilities.DokkaAnalysisModule
import org.jetbrains.dokka.buildDocumentationModule
import org.jetbrains.dokka.prepareForGeneration
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.utils.PathUtil
import java.nio.file.Path

internal class DokkaSourceScanner private constructor(
    private val request: PluginToolsRequest,
    private val logger: DokkaLogger
) {
    constructor(
        logger: Logger,
        request: PluginToolsRequest
    ) : this(
        request,
        MavenDokkaLogger(
            logger
        )
    )

    private val includes: List<String> = emptyList()

    private val options = DocumentationOptions(
        "",
        "html",
        sourceLinks = emptyList(),
        jdkVersion = 8,
        skipDeprecated = false,
        skipEmptyPackages = true,
        reportUndocumented = false,
        impliedPlatforms = emptyList(),
        perPackageOptions = emptyList(),
        externalDocumentationLinks = emptyList(),
        noStdlibLink = false,
        cacheRoot = null,
        languageVersion = null,
        apiVersion = null
    )

    private val documentationModule = DocumentationModule(request.project.artifactId!!)

    fun scanSourceDoc(requests: List<SourceScanRequest>): Map<String, ClassDoc> {
        for (sourceScanRequest in requests) {
            appendSourceModule(sourceScanRequest)
        }
        documentationModule.prepareForGeneration(options)

        return documentationModule.toClassDocs()
    }


    private fun appendSourceModule(sourceScanRequest: SourceScanRequest) {
        val environment = sourceScanRequest.createAnalysisEnvironment()

        logger.info("Module: ${request.project.artifactId}")
        logger.info("Sources: ${sourceScanRequest.sourceDirectories.joinToString()}")
        logger.info("Classpath: ${environment.classpath.joinToString()}")

        logger.info("Analysing sources and libraries... ")
        val startAnalyse = System.currentTimeMillis()

        val injector = Guice.createInjector(
            DokkaAnalysisModule(
                environment,
                options,
                JvmPlatformProvider,
                documentationModule.nodeRefGraph,
                logger
            )
        )

        buildDocumentationModule(injector, documentationModule, { true }, includes)

        val timeAnalyse = System.currentTimeMillis() - startAnalyse
        logger.info("done in ${timeAnalyse / 1000} secs")

        Disposer.dispose(environment)
    }

    private fun SourceScanRequest.createAnalysisEnvironment(): AnalysisEnvironment = when (this) {
        is SourceScanRequest.ArtifactScanRequest ->
            createAnalysisEnvironment(sourceDirectories, request.dependencies!!)

        is SourceScanRequest.ProjectScanRequest ->
            createAnalysisEnvironment(sourceDirectories, project.artifacts!!)
    }

    private fun createAnalysisEnvironment(
        sourceDirectories: List<Path>,
        dependencies: Set<Artifact>
    ): AnalysisEnvironment {
        val environment = AnalysisEnvironment(DokkaMessageCollector(logger))

        environment.apply {
            addClasspath(PathUtil.getJdkClassesRootsFromCurrentJre())

            for (dependency in dependencies) {
                addClasspath(dependency.file)
            }

            addSources(sourceDirectories.map(Path::toString))

            loadLanguageVersionSettings(options.languageVersion, options.apiVersion)
        }

        return environment
    }

    object JvmPlatformProvider : DefaultPlatformsProvider {
        override fun getDefaultPlatforms(descriptor: DeclarationDescriptor): List<String> =
            listOf("JVM")
    }
}
