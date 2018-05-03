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
package com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.qdox

import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.ClassDoc
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.SourceScanRequest
import com.thoughtworks.qdox.JavaProjectBuilder
import com.thoughtworks.qdox.library.SortedClassLibraryBuilder
import com.thoughtworks.qdox.model.JavaClass
import org.apache.maven.artifact.Artifact
import org.apache.maven.tools.plugin.PluginToolsRequest
import org.codehaus.plexus.logging.Logger
import java.net.MalformedURLException
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

internal class QDoxSourceScanner(
    private val logger: Logger,
    private val request: PluginToolsRequest
) {

    fun scanSourceDoc(requests: List<SourceScanRequest>): Map<String, ClassDoc> {
        val encoding = request.encoding!!
        val requestDependencies: Set<Artifact> = request.dependencies!!

        return requests.map {
            when (it) {
                is SourceScanRequest.ArtifactScanRequest -> {
                    it.sourceDirectories.discoverClasses(encoding, requestDependencies)
                }

                is SourceScanRequest.ProjectScanRequest -> {
                    it.sourceDirectories.discoverClasses(encoding, it.project.artifacts!!)
                }
            }
        }.fold(mutableMapOf()) { acc, element -> acc.putAll(element); acc }
    }

    private fun List<Path>.discoverClasses(
        encoding: String,
        artifacts: Set<Artifact>
    ): Map<String, ClassDoc> {
        val sourceDirectories = this

        val classpath = artifacts.map { it.file!! }

        logger.debug("Performing source scanning using QDox")
        logger.debug("Sources: ${this.joinToString()}")
        logger.debug("Classpath: ${classpath.joinToString()}")

        val startScanMillis = System.currentTimeMillis()

        // Build isolated Classloader with only the artifacts of the project (none of this plugin)
        val classLoader = URLClassLoader(
            classpath.asSequence()
                .mapNotNull({
                    try {
                        it.toURI().toURL()!!
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

        val scanDurationMillis = System.currentTimeMillis() - startScanMillis
        logger.debug("done in ${SECONDS.convert(scanDurationMillis, MILLISECONDS)} secs")

        val javaClasses: MutableCollection<JavaClass> = builder.classes ?: return emptyMap()

        return javaClasses.associateBy { it.fullyQualifiedName!! }.toClassDocMap()
    }
}
