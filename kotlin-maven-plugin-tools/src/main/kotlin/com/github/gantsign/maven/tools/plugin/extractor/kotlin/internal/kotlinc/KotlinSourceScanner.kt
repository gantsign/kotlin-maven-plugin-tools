/*-
 * #%L
 * kotlin-maven-plugin-tools
 * %%
 * Copyright (C) 2018 - 2022 GantSign Ltd.
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
package com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.kotlinc

import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.ClassDoc
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.SourceScanRequest
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import org.codehaus.plexus.logging.Logger
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

private fun createProject(logger: Logger): Project {
    val configuration = CompilerConfiguration()
    configuration.put(
        CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
        MavenMessageCollector(logger, MessageRenderer.PLAIN_RELATIVE_PATHS)
    )
    return KotlinCoreEnvironment.createForProduction(
        Disposer.newDisposable(), configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
    ).project
}

internal class KotlinSourceScanner private constructor(
    private val project: Project
) {
    constructor(logger: Logger) : this(createProject(logger))

    private fun createKtFile(codeString: String, fileName: String) =
        PsiManager.getInstance(project)
            .findFile(
                LightVirtualFile(fileName, KotlinFileType.INSTANCE, codeString)
            ) as KtFile

    fun scanSourceDoc(requests: List<SourceScanRequest>): Map<String, ClassDoc> = requests.stream()
        .flatMap { it.sourceDirectories.stream() }
        .flatMap { Files.walk(it) }
        .filter { Files.isRegularFile(it) }
        .filter { it.toString().endsWith(".kt") }
        .flatMap { it.parse().stream() }
        .collect(Collectors.toMap(ClassDoc::fullyQualifiedName, { it }))

    private fun Path.parse(): List<ClassDoc> {
        val ktFile = createKtFile(String(Files.readAllBytes(this), UTF_8), toFile().path)
        val classes = ktFile.findChildrenByClass(KtClass::class.java)
        return classes.map(KtClass::toClassDoc)
    }
}
