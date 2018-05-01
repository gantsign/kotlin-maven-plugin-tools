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

import com.thoughtworks.qdox.JavaProjectBuilder
import com.thoughtworks.qdox.library.SortedClassLibraryBuilder
import com.thoughtworks.qdox.model.DocletTag
import com.thoughtworks.qdox.model.JavaClass
import com.thoughtworks.qdox.model.JavaField
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.resolver.ArtifactNotFoundException
import org.apache.maven.artifact.resolver.ArtifactResolutionException
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest
import org.apache.maven.plugin.descriptor.InvalidParameterException
import org.apache.maven.plugin.descriptor.MojoDescriptor
import org.apache.maven.plugin.descriptor.Parameter
import org.apache.maven.plugin.descriptor.PluginDescriptor
import org.apache.maven.plugin.descriptor.Requirement
import org.apache.maven.project.MavenProject
import org.apache.maven.repository.RepositorySystem
import org.apache.maven.tools.plugin.ExtendedMojoDescriptor
import org.apache.maven.tools.plugin.PluginToolsRequest
import org.apache.maven.tools.plugin.extractor.ExtractionException
import org.apache.maven.tools.plugin.extractor.MojoDescriptorExtractor
import org.apache.maven.tools.plugin.extractor.annotations.datamodel.ComponentAnnotationContent
import org.apache.maven.tools.plugin.extractor.annotations.datamodel.ExecuteAnnotationContent
import org.apache.maven.tools.plugin.extractor.annotations.datamodel.ParameterAnnotationContent
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotatedClass
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotationsScanner
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotationsScannerRequest
import org.apache.maven.tools.plugin.util.PluginUtils
import org.codehaus.plexus.archiver.manager.ArchiverManager
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException
import org.codehaus.plexus.component.annotations.Component
import org.codehaus.plexus.logging.AbstractLogEnabled
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.codehaus.plexus.component.annotations.Requirement as PlexusRequirement

/**
 * KotlinDescriptorExtractor, a MojoDescriptor extractor to read descriptors from Kotlin.
 */
@Component(role = MojoDescriptorExtractor::class, hint = "kotlin")
class JavaAnnotationsMojoDescriptorExtractor : AbstractLogEnabled(), MojoDescriptorExtractor {

    @PlexusRequirement
    private lateinit var mojoAnnotationsScanner: MojoAnnotationsScanner

    @PlexusRequirement
    private lateinit var repositorySystem: RepositorySystem

    @PlexusRequirement
    private lateinit var archiverManager: ArchiverManager

    override fun execute(request: PluginToolsRequest): List<MojoDescriptor> {
        val mojoAnnotatedClasses = request.scanAnnotations()

        val javaClassesMap = request.scanJavadoc(mojoAnnotatedClasses.values)

        populateDataFromJavadoc(mojoAnnotatedClasses, javaClassesMap)

        return toMojoDescriptors(mojoAnnotatedClasses, request.pluginDescriptor)
    }

    private fun PluginToolsRequest.scanAnnotations(): Map<String, MojoAnnotatedClass> {
        val request = this

        return mojoAnnotationsScanner.scan(MojoAnnotationsScannerRequest().apply {
            classesDirectories = listOf(File(request.project.build.outputDirectory!!))
            dependencies = request.dependencies!!
            project = request.project!!
        })
    }

    private fun PluginToolsRequest.scanJavadoc(
        mojoAnnotatedClasses: Collection<MojoAnnotatedClass>
    ): Map<String, JavaClass> {
        val request = this
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

            return request.let {
                discoverClasses(
                    it.encoding,
                    listOf(extractDirectory),
                    it.dependencies
                )
            }
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

    /**
     * Scan sources to get @since and @deprecated and description of classes and fields.
     */
    private fun populateDataFromJavadoc(
        mojoAnnotatedClasses: Map<String, MojoAnnotatedClass>,
        javaClassesMap: Map<String, JavaClass>
    ) {
        for ((className, mojoAnnotatedClass) in mojoAnnotatedClasses) {
            val javaClass = javaClassesMap[className] ?: continue

            // populate class-level content
            mojoAnnotatedClass.mojo?.also { mojoAnnotationContent ->
                mojoAnnotationContent.description = javaClass.comment

                javaClass.findInClassHierarchy("since")
                    ?.let { mojoAnnotationContent.since = it.value }

                javaClass.findInClassHierarchy("deprecated")
                    ?.let { mojoAnnotationContent.deprecated = it.value }
            }

            val fieldsMap = javaClass.extractFieldParameterTags(javaClassesMap)

            // populate parameters
            val parameters: Map<String, ParameterAnnotationContent> =
                mojoAnnotatedClass.gatherParametersFromClassHierarchy(mojoAnnotatedClasses)
                    .toSortedMap()

            for ((fieldName, parameterAnnotationContent) in parameters) {
                val javaField = fieldsMap[fieldName] ?: continue

                parameterAnnotationContent.description = javaField.comment

                javaField.getTagByName("deprecated")
                    ?.let { parameterAnnotationContent.deprecated = it.value }

                javaField.getTagByName("since")
                    ?.let { parameterAnnotationContent.since = it.value }
            }

            // populate components
            val components: Map<String, ComponentAnnotationContent> =
                mojoAnnotatedClass.components!!

            for ((fieldName, componentAnnotationContent) in components) {
                val javaField = fieldsMap[fieldName] ?: continue

                componentAnnotationContent.description = javaField.comment

                javaField.getTagByName("deprecated")
                    ?.let { componentAnnotationContent.deprecated = it.value }

                javaField.getTagByName("since")
                    ?.let { componentAnnotationContent.since = it.value }
            }
        }
    }

    private tailrec fun JavaClass.findInClassHierarchy(tagName: String): DocletTag? {
        val tag: DocletTag? = getTagByName(tagName)

        if (tag != null) return tag

        val superClass: JavaClass = superJavaClass ?: return null

        return superClass.findInClassHierarchy(tagName)
    }

    /**
     * Extract fields that are either parameters or components.
     *
     * @return map with Mojo parameters names as keys.
     */
    private tailrec fun JavaClass.extractFieldParameterTags(
        javaClassesMap: Map<String, JavaClass>,
        descendantParams: Map<String, JavaField> = mapOf()
    ): MutableMap<String, JavaField> {

        val superClass: JavaClass? = superJavaClass

        val searchSuperClass = when {
            superClass == null -> null
            superClass.fields.isNotEmpty() -> superClass
            else -> {
                // maybe sources comes from scan of sources artifact
                javaClassesMap[superClass.fullyQualifiedName]
            }
        }

        val localParams: Map<String, JavaField> = fields.associateBy { it.name }

        // the descendant params must overwrite local (parent) params
        val mergedParams = localParams.toSortedMap().also { it.putAll(descendantParams) }
        if (searchSuperClass == null) {
            return mergedParams
        }
        return searchSuperClass.extractFieldParameterTags(javaClassesMap, mergedParams)
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

        return discoverClasses(encoding, sources, artifacts)
    }

    private fun discoverClasses(
        encoding: String, sourceDirectories: List<Path>,
        artifacts: Set<Artifact>
    ): Map<String, JavaClass> {

        // Build isolated Classloader with only the artifacts of the project (none of this plugin)
        val classLoader = URLClassLoader(
            artifacts.asSequence()
                .map({
                    try {
                        it.file.toURI().toURL()!!
                    } catch (e: MalformedURLException) {
                        null
                    }
                })
                .filterNotNull()
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

        val javaClasses: MutableCollection<JavaClass> = builder.classes
            ?.takeUnless(MutableCollection<JavaClass>::isEmpty)
            ?: return emptyMap()

        return javaClasses.associateBy { it.fullyQualifiedName!! }
    }

    private fun toMojoDescriptors(
        mojoAnnotatedClasses: Map<String, MojoAnnotatedClass>,
        pluginDescriptor: PluginDescriptor
    ): List<MojoDescriptor> = mojoAnnotatedClasses.values
        .filter { mojoAnnotatedClass ->
            // no mojo so skip it
            mojoAnnotatedClass.mojo != null
        }
        .map { mojoAnnotatedClass ->
            mojoAnnotatedClass.toMojoDescriptor(mojoAnnotatedClasses, pluginDescriptor)
        }

    private fun MojoAnnotatedClass.toMojoDescriptor(
        mojoAnnotatedClasses: Map<String, MojoAnnotatedClass>,
        pluginDescriptor: PluginDescriptor
    ): MojoDescriptor {
        val mojoAnnotatedClass = this

        return ExtendedMojoDescriptor().apply {

            implementation = mojoAnnotatedClass.className
            language = "java"

            val mojo = mojoAnnotatedClass.mojo!!

            description = mojo.description
            since = mojo.since
            mojo.deprecated = mojo.deprecated

            isProjectRequired = mojo.requiresProject()

            isRequiresReports = mojo.requiresReports()

            componentConfigurator = mojo.configurator()

            isInheritedByDefault = mojo.inheritByDefault()

            instantiationStrategy = mojo.instantiationStrategy().id()

            isAggregator = mojo.aggregator()
            isDependencyResolutionRequired = mojo.requiresDependencyResolution().id()
            dependencyCollectionRequired = mojo.requiresDependencyCollection().id()

            isDirectInvocationOnly = mojo.requiresDirectInvocation()
            deprecated = mojo.deprecated
            isThreadSafe = mojo.threadSafe()


            mojoAnnotatedClass.findExecuteInClassHierarchy(mojoAnnotatedClasses)?.also { execute ->
                executeGoal = execute.goal()
                executeLifecycle = execute.lifecycle()
                execute.phase()?.also { executePhase = it.id() }
            }

            executionStrategy = mojo.executionStrategy()

            goal = mojo.name()
            isOnlineRequired = mojo.requiresOnline()

            phase = mojo.defaultPhase().id()

            // Parameter annotations
            mojoAnnotatedClass.gatherParametersFromClassHierarchy(mojoAnnotatedClasses)
                .values
                .toSortedSet()
                .forEach { parameterAnnotationContent ->
                    addParameter(parameterAnnotationContent.toParameter())
                }

            // Component annotations
            mojoAnnotatedClass.gatherComponentsFromClassHierarchy(mojoAnnotatedClasses)
                .values
                .toSortedSet()
                .forEach { componentAnnotationContent ->
                    addParameter(componentAnnotationContent.toParameter(mojoAnnotatedClass))
                }

            this.pluginDescriptor = pluginDescriptor
        }
    }

    private fun ParameterAnnotationContent.toParameter(): Parameter {
        val parameterAnnotationContent = this

        return Parameter().apply {
            name = parameterAnnotationContent.name()
                ?.takeUnless(String::isEmpty)
                ?: parameterAnnotationContent.fieldName
            alias = parameterAnnotationContent.alias()
            defaultValue = parameterAnnotationContent.defaultValue()
            deprecated = parameterAnnotationContent.deprecated
            description = parameterAnnotationContent.description
            isEditable = !parameterAnnotationContent.readonly()
            val property: String? = parameterAnnotationContent.property()
            if (property?.let { it.contains('$') || it.contains('{') || it.contains('}') } == true) {
                throw InvalidParameterException(
                    "Invalid property for parameter '$name', forbidden characters '\${}': $property",
                    null
                )
            }
            expression = property?.let { "\${$it}" } ?: ""
            type = parameterAnnotationContent.className
            since = parameterAnnotationContent.since
            isRequired = parameterAnnotationContent.required()
        }
    }

    private fun ComponentAnnotationContent.toParameter(mojoAnnotatedClass: MojoAnnotatedClass): Parameter {
        val componentAnnotationContent = this

        return Parameter().apply {
            name = componentAnnotationContent.fieldName

            // recognize Maven-injected objects as components annotations instead of parameters
            @Suppress("DEPRECATION")
            val expression = PluginUtils.MAVEN_COMPONENTS[componentAnnotationContent.roleClassName]

            if (expression == null) {
                // normal component
                requirement = Requirement(
                    componentAnnotationContent.roleClassName,
                    componentAnnotationContent.hint()
                )
            } else {
                // not a component but a Maven object to be transformed into an expression/property: deprecated
                logger.warn("Deprecated @Component annotation for '$name' field in ${mojoAnnotatedClass.className}: replace with @Parameter( defaultValue = \"$expression\", readonly = true )")
                defaultValue = expression
                type = componentAnnotationContent.roleClassName
                isRequired = true
            }
            deprecated = componentAnnotationContent.deprecated
            since = componentAnnotationContent.since
            isEditable = false
        }
    }

    private tailrec fun MojoAnnotatedClass.findExecuteInClassHierarchy(
        mojoAnnotatedClasses: Map<String, MojoAnnotatedClass>
    ): ExecuteAnnotationContent? {
        val mojoAnnotatedClass = this

        if (mojoAnnotatedClass.execute != null) {
            return mojoAnnotatedClass.execute
        }

        val parentClassName: String = mojoAnnotatedClass.parentClassName
            ?.takeUnless(String::isEmpty)
            ?: return null

        val parent = mojoAnnotatedClasses[parentClassName] ?: return null
        return parent.findExecuteInClassHierarchy(mojoAnnotatedClasses)
    }

    private tailrec fun MojoAnnotatedClass.gatherParametersFromClassHierarchy(
        mojoAnnotatedClasses: Map<String, MojoAnnotatedClass>,
        descendantParameterAnnotationContents: Map<String, ParameterAnnotationContent> = mapOf()
    ): Map<String, ParameterAnnotationContent> {
        val mojoAnnotatedClass = this

        val localParameterAnnotationContents =
            mojoAnnotatedClass.parameters.values.associateBy { it.fieldName!! }

        val mergedParameterAnnotationContents =
            localParameterAnnotationContents + descendantParameterAnnotationContents

        val parentClassName: String =
            mojoAnnotatedClass.parentClassName ?: return mergedParameterAnnotationContents

        val parent =
            mojoAnnotatedClasses[parentClassName] ?: return mergedParameterAnnotationContents

        return parent.gatherParametersFromClassHierarchy(
            mojoAnnotatedClasses,
            mergedParameterAnnotationContents
        )
    }

    private tailrec fun MojoAnnotatedClass.gatherComponentsFromClassHierarchy(
        mojoAnnotatedClasses: Map<String, MojoAnnotatedClass>,
        descendantComponentAnnotationContents: Map<String, ComponentAnnotationContent> = mapOf()
    ): Map<String, ComponentAnnotationContent> {
        val mojoAnnotatedClass = this

        val localComponentAnnotationContent =
            mojoAnnotatedClass.components.values.associateBy { it.fieldName!! }

        val mergedComponentAnnotationContents =
            localComponentAnnotationContent + descendantComponentAnnotationContents

        val parentClassName: String =
            mojoAnnotatedClass.parentClassName ?: return mergedComponentAnnotationContents

        val parent =
            mojoAnnotatedClasses[parentClassName] ?: return mergedComponentAnnotationContents

        return parent.gatherComponentsFromClassHierarchy(
            mojoAnnotatedClasses,
            mergedComponentAnnotationContents
        )
    }

    private fun Artifact.fromProjectReferences(project: MavenProject): MavenProject? =
        project.projectReferences
            ?.takeUnless(MutableMap<String, MavenProject>::isEmpty)
            ?.let { projectReferences ->
                projectReferences.values.let { mavenProjects ->
                    mavenProjects.firstOrNull { it.id == id }
                }
            }
}
