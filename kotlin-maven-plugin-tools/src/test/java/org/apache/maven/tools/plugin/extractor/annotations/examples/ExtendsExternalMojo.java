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

import java.io.File;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.jar.AbstractJarMojo;

/** @deprecated Extends external deprecated. */
@Deprecated
@SuppressWarnings("ALL")
@Mojo(
  name = "extendsexternal",
  defaultPhase = LifecyclePhase.COMPILE,
  requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
  requiresDependencyCollection = ResolutionScope.RUNTIME,
  instantiationStrategy = InstantiationStrategy.SINGLETON,
  executionStrategy = "always",
  requiresProject = false,
  requiresDirectInvocation = true,
  requiresOnline = true,
  inheritByDefault = false,
  configurator = "externalExternalConfigurator",
  threadSafe = true
)
@Execute(lifecycle = "extendsExternalLifecycle", phase = LifecyclePhase.PACKAGE)
public class ExtendsExternalMojo extends AbstractJarMojo {

  @Parameter protected String additionalParameter;

  @Override
  protected File getClassesDirectory() {
    return null;
  }

  @Override
  protected String getClassifier() {
    return null;
  }

  @Override
  protected String getType() {
    return null;
  }

  @Override
  public void execute() {
    // nothing
  }
}
