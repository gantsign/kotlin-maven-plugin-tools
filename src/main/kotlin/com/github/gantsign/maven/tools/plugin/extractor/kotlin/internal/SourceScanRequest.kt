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

import org.apache.maven.artifact.Artifact
import org.apache.maven.project.MavenProject
import java.nio.file.Path

internal sealed class SourceScanRequest(open val sourceDirectories: List<Path>) {

    data class ArtifactScanRequest(
        val artifact: Artifact,
        private val sourceDirectory: Path
    ) : SourceScanRequest(listOf(sourceDirectory))

    data class ProjectScanRequest(
        val project: MavenProject,
        override val sourceDirectories: List<Path>
    ) : SourceScanRequest(sourceDirectories)
}
