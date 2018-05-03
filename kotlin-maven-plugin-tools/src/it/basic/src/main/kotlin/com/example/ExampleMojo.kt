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
package com.example

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.InstantiationStrategy
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.repository.RepositorySystem

/**
 * Example description.
 *
 * @since exampleVersion
 */
@SuppressWarnings("ALL")
@Mojo(
    name = "example",
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
    configurator = "exampleConfigurator",
    threadSafe = true
)
@Execute(goal = "compiler", lifecycle = "exampleLifecycle", phase = LifecyclePhase.PACKAGE)
@Deprecated("Example deprecated.")
open class ExampleMojo : AbstractMojo() {

    /**
     * Example parameter description.
     *
     * @since exampleParameterVersion
     */
    @Parameter(
        name = "exampleParameterName",
        alias = "exampleParameterAlias",
        property = "exampleParameterProperty",
        defaultValue = "exampleParameterDefaultValue",
        required = true,
        readonly = false
    )
    @Deprecated("Example parameter deprecated message.")
    var exampleParameter: String? = null

    /**
     * Example component description.
     *
     * @since exampleComponentVersion
     */
    @Component(role = RepositorySystem::class, hint = "exampleComponentHint")
    @Deprecated("Example component deprecated message.")
    var exampleComponent: RepositorySystem? = null

    override fun execute() {
        // nothing
    }
}
