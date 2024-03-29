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

import java.io.File
import org.apache.maven.tools.plugin.PluginToolsRequest
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotatedClass
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotationsScanner
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotationsScannerRequest

internal class AnnotationScanner(
    private val mojoAnnotationsScanner: MojoAnnotationsScanner
) {

    fun scanAnnotations(request: PluginToolsRequest): Map<String, MojoAnnotatedClass> {
        return mojoAnnotationsScanner.scan(
            MojoAnnotationsScannerRequest().apply {
                classesDirectories = listOf(File(request.project.build.outputDirectory!!))
                dependencies = request.dependencies!!
                project = request.project!!
            }
        )
    }
}
