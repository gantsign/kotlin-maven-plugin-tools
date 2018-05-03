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
package com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.dokka

import org.codehaus.plexus.logging.Logger
import org.jetbrains.dokka.DokkaLogger

internal class MavenDokkaLogger(private val log: Logger) : DokkaLogger {
    override fun error(message: String) {
        log.error(message)
    }

    override fun info(message: String) {
        log.info(message)
    }

    override fun warn(message: String) {
        log.warn(message)
    }
}
