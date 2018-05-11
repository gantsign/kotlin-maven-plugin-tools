package com.github.gantsign.maven.tools.plugin.extractor.kotlin.examples

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

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.InstantiationStrategy
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.repository.RepositorySystem
import org.codehaus.plexus.compiler.manager.CompilerManager
import org.codehaus.plexus.compiler.manager.DefaultCompilerManager

/**
 * Kotlin local description.
 *
 * @since kotlinLocalVersion
 */
@SuppressWarnings("ALL")
@Mojo(
    name = "kotlin",
    defaultPhase = LifecyclePhase.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    requiresDependencyCollection = ResolutionScope.RUNTIME,
    instantiationStrategy = InstantiationStrategy.SINGLETON,
    executionStrategy = "always",
    requiresProject = false,
    requiresReports = true,
    aggregator = true,
    requiresDirectInvocation = true,
    requiresOnline = true,
    inheritByDefault = false,
    configurator = "kotlinLocalConfigurator",
    threadSafe = true
)
@Execute(goal = "compiler", lifecycle = "kotlinLocalLifecycle", phase = LifecyclePhase.PACKAGE)
@Deprecated("Kotlin local deprecated.")
open class KotlinMojo : AbstractMojo() {

    @Parameter
    var minimalParameter: String? = null

    /**
     * Everything parameter description.
     *
     * Paragraph 2.
     *
     * This is a [link](https://example.com).
     *
     * This is *text with emphasis*.
     *
     * This is **strong text**.
     *
     * This is `inline code`.
     *
     * @since everythingParameterVersion
     */
    @Parameter(
        name = "everythingParameterName",
        alias = "everythingParameterAlias",
        property = "everythingParameterProperty",
        defaultValue = "everythingParameterDefaultValue",
        required = true,
        readonly = true
    )
    @Deprecated("everything parameter deprecated message")
    var everythingParameter: String? = null

    @Component
    var minimalComponent: RepositorySystem? = null

    /**
     * Everything component description.
     *
     * @since everythingComponentVersion
     */
    @Component(role = CompilerManager::class, hint = "everythingComponentHint")
    @Deprecated("everything component deprecated message")
    var everythingComponent: DefaultCompilerManager? = null

    @Parameter(defaultValue = "\${mojoExecution}", readonly = true)
    val componentAsParameter: MojoExecution? = null

    override fun execute() {
        // nothing
    }
}
