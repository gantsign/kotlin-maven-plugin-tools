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

import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.codehaus.plexus.logging.Logger
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.junit.Test

class MavenMessageCollectorTest {

    @Test
    fun report() {
        val logger = mockk<Logger>(relaxed = true)
        val collector = MavenMessageCollector(logger, MessageRenderer.PLAIN_RELATIVE_PATHS)

        assertThat(collector.hasErrors()).isFalse()
        collector.report(CompilerMessageSeverity.ERROR, "test1", null)

        verify { logger.warn("error: test1") }

        assertThat(collector.hasErrors()).isTrue()
        collector.clear()
        assertThat(collector.hasErrors()).isFalse()

        confirmVerified(logger)
    }
}
