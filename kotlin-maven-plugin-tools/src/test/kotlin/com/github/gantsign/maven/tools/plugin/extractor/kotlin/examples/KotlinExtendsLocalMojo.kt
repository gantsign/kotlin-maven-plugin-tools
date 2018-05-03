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

import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.InstantiationStrategy
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.tools.plugin.extractor.annotations.examples.LocalMojo
import org.codehaus.plexus.compiler.manager.CompilerManager
import org.codehaus.plexus.compiler.manager.DefaultCompilerManager

/**
 * Kotlin extends local description.
 *
 * @since kotlinExtendsLocalVersion
 */
@SuppressWarnings("ALL")
@Mojo(
    name = "kotlinextendslocal",
    defaultPhase = LifecyclePhase.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
    requiresDependencyCollection = ResolutionScope.RUNTIME,
    instantiationStrategy = InstantiationStrategy.SINGLETON,
    executionStrategy = "always",
    requiresProject = false,
    requiresDirectInvocation = true,
    requiresOnline = true,
    inheritByDefault = false,
    configurator = "kotlinExtendsLocalConfigurator",
    threadSafe = true
)
@Execute(
    lifecycle = "kotlinExtendsLocalLifecycle",
    phase = LifecyclePhase.PACKAGE
)
@Deprecated("Kotlin extends local deprecated.")
class KotlinExtendsLocalMojo : LocalMojo() {

    @Parameter(name = "everythingParameterName", alias = "extendsEverythingParameterAlias")
    @Deprecated("extends everything parameter deprecated message")
    var everythingParameter: String? = null

    @Parameter
    var additionalParameter: String? = null

    @Component(role = CompilerManager::class, hint = "extendsEverythingComponentHint")
    @Deprecated("extends everything component deprecated message")
    var everythingComponent: DefaultCompilerManager? = null

    override fun execute() {
        // nothing
    }
}
