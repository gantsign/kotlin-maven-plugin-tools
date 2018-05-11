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

import org.jetbrains.dokka.ContentBlock
import org.jetbrains.dokka.ContentCode
import org.jetbrains.dokka.ContentEmphasis
import org.jetbrains.dokka.ContentEntity
import org.jetbrains.dokka.ContentExternalLink
import org.jetbrains.dokka.ContentHeading
import org.jetbrains.dokka.ContentIdentifier
import org.jetbrains.dokka.ContentKeyword
import org.jetbrains.dokka.ContentNode
import org.jetbrains.dokka.ContentNonBreakingSpace
import org.jetbrains.dokka.ContentParagraph
import org.jetbrains.dokka.ContentStrong
import org.jetbrains.dokka.ContentSymbol
import org.jetbrains.dokka.ContentText
import org.jetbrains.dokka.htmlEscape

internal class HtmlStringBuilder {
    private val to: StringBuilder = StringBuilder()

    private fun appendText(text: String) {
        to.append(text.htmlEscape())
    }

    private fun appendHeader(level: Int, body: () -> Unit) =
        wrapInTag("h$level", body, newlineBeforeOpen = true, newlineAfterClose = true)

    private fun appendParagraph(body: () -> Unit) {
        if (to.isNotEmpty()) {
            to.append("<br/>\n")
        }
        body()
    }

    private fun appendLink(href: String, body: () -> Unit) =
        wrap("<a href=\"$href\">", "</a>", body)

    private fun appendStrong(body: () -> Unit) = wrapInTag("strong", body)
    private fun appendEmphasis(body: () -> Unit) = wrapInTag("em", body)
    private fun appendCode(body: () -> Unit) = wrapInTag("code", body)

    private fun appendNonBreakingSpace() {
        to.append("&nbsp;")
    }

    private fun wrap(prefix: String, suffix: String, body: () -> Unit) {
        to.append(prefix)
        body()
        to.append(suffix)
    }

    private fun wrapInTag(
        tag: String,
        body: () -> Unit,
        newlineBeforeOpen: Boolean = false,
        newlineAfterOpen: Boolean = false,
        newlineAfterClose: Boolean = false
    ) {
        if (newlineBeforeOpen && !to.endsWith('\n')) to.appendln()
        to.append("<$tag>")
        if (newlineAfterOpen) to.appendln()
        body()
        to.append("</$tag>")
        if (newlineAfterClose) to.appendln()
    }


    private fun appendLinkIfNotThisPage(href: String, content: ContentBlock) {
        if (href == ".") {
            appendContent(content.children)
        } else {
            appendLink(href) { appendContent(content.children) }
        }
    }

    private fun appendContent(content: ContentNode) {
        when (content) {
            is ContentText -> appendText(content.text)
            is ContentSymbol -> appendText(content.text)
            is ContentKeyword -> appendText(content.text)
            is ContentIdentifier -> appendText(content.text)
            is ContentNonBreakingSpace -> appendNonBreakingSpace()
            is ContentEntity -> appendText(content.text)
            is ContentStrong -> appendStrong { appendContent(content.children) }
            is ContentCode -> appendCode { appendContent(content.children) }
            is ContentEmphasis -> appendEmphasis { appendContent(content.children) }

            is ContentExternalLink -> appendLinkIfNotThisPage(content.href, content)

            is ContentParagraph -> {
                if (!content.isEmpty()) {
                    appendParagraph { appendContent(content.children) }
                }
            }

            is ContentHeading -> appendHeader(content.level) { appendContent(content.children) }
            is ContentBlock -> appendContent(content.children)
        }
    }

    fun appendContent(content: List<ContentNode>) {
        for (contentNode in content) {
            appendContent(contentNode)
        }
    }

    override fun toString(): String = to.toString()
}
