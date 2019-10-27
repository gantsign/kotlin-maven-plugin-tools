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
import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import org.apache.maven.tools.plugin.PluginToolsRequest
import org.codehaus.plexus.logging.Logger
import org.jetbrains.dokka.AnalysisEnvironment
import org.jetbrains.dokka.DefaultPlatformsProvider
import org.jetbrains.dokka.DocumentationModule
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaLogger
import org.jetbrains.dokka.DokkaMessageCollector
import org.jetbrains.dokka.Generation.DocumentationMerger
import org.jetbrains.dokka.PassConfigurationImpl
import org.jetbrains.dokka.Platform
import org.jetbrains.dokka.SourceRootImpl
import org.jetbrains.dokka.Utilities.DokkaAnalysisModule
import org.jetbrains.dokka.Utilities.DokkaRunModule
import org.jetbrains.dokka.buildDocumentationModule
import org.jetbrains.dokka.prepareForGeneration
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.utils.PathUtil

internal class DokkaSourceScanner private constructor(
    private val logger: Logger,
    private val dokkaLogger: DokkaLogger,
    private val request: PluginToolsRequest
) {
    constructor(
        logger: Logger,
        request: PluginToolsRequest
    ) : this(logger, MavenDokkaLogger(logger), request)

    private fun SourceScanRequest.classpath(): List<String> = when (this) {
        is SourceScanRequest.ArtifactScanRequest ->
            request.dependencies.map { it.file.getPath() }

        is SourceScanRequest.ProjectScanRequest ->
            project.artifacts.map { it.file.getPath() }
    }

    private fun SourceScanRequest.sourceRoots(): List<SourceRootImpl> =
        sourceDirectories.map { SourceRootImpl(it.toFile().getPath()) }

    private fun createDokkaConfiguration(requests: List<SourceScanRequest>): DokkaConfiguration {
        val passConfigurations: List<PassConfigurationImpl> = requests.map { scanRequest ->
            PassConfigurationImpl(
                classpath = scanRequest.classpath(),
                sourceRoots = scanRequest.sourceRoots(),
                samples = emptyList(),
                includes = emptyList(),
                collectInheritedExtensionsFromLibraries = false,
                sourceLinks = emptyList(),
                jdkVersion = 8,
                skipDeprecated = false,
                skipEmptyPackages = true,
                reportUndocumented = false,
                perPackageOptions = emptyList(),
                externalDocumentationLinks = emptyList(),
                noStdlibLink = false,
                noJdkLink = false,
                languageVersion = null,
                apiVersion = null,
                moduleName = request.project.artifactId,
                suppressedFiles = emptyList(),
                sinceKotlin = null,
                analysisPlatform = Platform.DEFAULT,
                targets = emptyList(),
                includeNonPublic = true,
                includeRootPackage = false
            )
        }

        return DokkaConfigurationImpl(
            outputDir = "",
            format = "html",
            impliedPlatforms = emptyList(),
            cacheRoot = null,
            passesConfigurations = passConfigurations,
            generateIndexPages = false
        )
    }

    fun scanSourceDoc(requests: List<SourceScanRequest>): Map<String, ClassDoc> {

        val dokkaConfiguration: DokkaConfiguration = createDokkaConfiguration(requests)

        val globalInjector = Guice.createInjector(DokkaRunModule(dokkaConfiguration))

        fun appendSourceModule(
            passConfiguration: DokkaConfiguration.PassConfiguration,
            documentationModule: DocumentationModule
        ) = with(passConfiguration) {

            val sourcePaths = passConfiguration.sourceRoots.map { it.path }
            val environment = createAnalysisEnvironment(sourcePaths, passConfiguration)

            logger.debug("Performing source scanning using Dokka")
            logger.debug("Sources: ${sourcePaths.joinToString()}")
            logger.debug("Classpath: ${environment.classpath.joinToString()}")

            val startScanMillis = System.currentTimeMillis()

            val defaultPlatformAsList = passConfiguration.targets
            val defaultPlatformsProvider = object : DefaultPlatformsProvider {
                override fun getDefaultPlatforms(descriptor: DeclarationDescriptor): List<String> {
                    if (descriptor is MemberDescriptor && descriptor.isExpect) {
                        return defaultPlatformAsList.take(1)
                    }
                    return defaultPlatformAsList
                }
            }

            val injector = globalInjector.createChildInjector(
                DokkaAnalysisModule(
                    environment,
                    dokkaConfiguration,
                    defaultPlatformsProvider,
                    documentationModule.nodeRefGraph,
                    passConfiguration,
                    dokkaLogger
                )
            )

            buildDocumentationModule(injector, documentationModule, { true }, includes)

            val scanDurationMillis = System.currentTimeMillis() - startScanMillis
            logger.debug("done in ${SECONDS.convert(scanDurationMillis, MILLISECONDS)} secs")

            Disposer.dispose(environment)
        }

        val documentationModules: MutableList<DocumentationModule> = mutableListOf()

        for (pass in dokkaConfiguration.passesConfigurations) {
            val documentationModule = DocumentationModule(pass.moduleName)
            appendSourceModule(pass, documentationModule)
            documentationModules.add(documentationModule)
        }

        val totalDocumentationModule = DocumentationMerger(documentationModules, dokkaLogger).merge()
        totalDocumentationModule.prepareForGeneration(dokkaConfiguration)

        return totalDocumentationModule.toClassDocs()
    }

    fun createAnalysisEnvironment(
        sourcePaths: List<String>,
        passConfiguration: DokkaConfiguration.PassConfiguration
    ): AnalysisEnvironment {
        val environment = AnalysisEnvironment(DokkaMessageCollector(dokkaLogger), passConfiguration.analysisPlatform)

        environment.apply {
            addClasspath(PathUtil.getJdkClassesRootsFromCurrentJre())

            for (element in passConfiguration.classpath) {
                addClasspath(File(element))
            }

            addSources(sourcePaths)

            loadLanguageVersionSettings(passConfiguration.languageVersion, passConfiguration.apiVersion)
        }

        return environment
    }
}
