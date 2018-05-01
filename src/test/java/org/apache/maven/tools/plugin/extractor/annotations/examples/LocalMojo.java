package org.apache.maven.tools.plugin.extractor.annotations.examples;

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

import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.compiler.manager.CompilerManager;
import org.codehaus.plexus.compiler.manager.DefaultCompilerManager;

/**
 * Local description.
 *
 * @deprecated Local deprecated.
 * @since localVersion
 */
@Deprecated
@SuppressWarnings("ALL")
@Mojo(
  name = "local",
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
  configurator = "localConfigurator",
  threadSafe = true
)
@Execute(goal = "compiler", lifecycle = "localLifecycle", phase = LifecyclePhase.PACKAGE)
public class LocalMojo extends AbstractMojo {

  @Parameter protected String minimalParameter;

  /**
   * Everything parameter description
   *
   * @deprecated everything parameter deprecated message
   * @since everythingParameterVersion
   */
  @Deprecated
  @Parameter(
    name = "everythingParameterName",
    alias = "everythingParameterAlias",
    property = "everythingParameterProperty",
    defaultValue = "everythingParameterDefaultValue",
    required = true,
    readonly = true
  )
  protected String everythingParameter;

  @Component protected ArtifactResolver minimalComponent;

  /**
   * Everything component description.
   *
   * @deprecated everything component deprecated message
   * @since everythingComponentVersion
   */
  @Deprecated
  @Component(role = CompilerManager.class, hint = "everythingComponentHint")
  protected DefaultCompilerManager everythingComponent;

  @Parameter(defaultValue = "${mojoExecution}", readonly = true)
  private MojoExecution componentAsParameter;

  @Override
  public void execute() {
    // nothing
  }
}
