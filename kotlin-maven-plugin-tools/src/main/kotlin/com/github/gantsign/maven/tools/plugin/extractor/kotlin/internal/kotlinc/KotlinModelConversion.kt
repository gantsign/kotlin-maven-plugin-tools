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
@file:Suppress("ktlint:filename")

package com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.kotlinc

import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.ClassDoc
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.DocTag
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.PropertyDoc
import java.net.URI
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.acceptChildren
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

private fun KDoc.since(): DocTag? =
    getDefaultSection().findTagByName("since")?.getContent()?.markdownToHtml()?.let(::DocTag)

private fun KtAnnotated.deprecated(): DocTag? = this.annotationEntries.asSequence()
    .filter { it.shortName!!.asString() == "Deprecated" }
    .map { annotationEntry ->
        val arguments = annotationEntry.valueArgumentList?.arguments ?: emptyList()
        return@map arguments.asSequence()
            .filter {
                val argumentName = it.getArgumentName()?.asName?.asString() ?: "message"
                return@filter argumentName == "message"
            }
            .map { it.getArgumentExpression()!!.asString() }
            .firstOrNull()
    }
    .map { DocTag(it ?: "") }
    .firstOrNull()

private fun tags(deprecated: DocTag?, since: DocTag?): Map<String, DocTag> {
    val tags = mutableMapOf<String, DocTag>()
    if (deprecated != null) {
        tags += mapOf("deprecated" to deprecated)
    }
    if (since != null) {
        tags += mapOf("since" to since)
    }
    return tags.toMap()
}

private fun KDoc.asString(): String = getDefaultSection().getContent().markdownToHtml()

private fun String.markdownToHtml(): String {
    val flavour = object : CommonMarkFlavourDescriptor() {
        override fun createHtmlGeneratingProviders(
            linkMap: LinkMap,
            baseURI: URI?
        ): Map<IElementType, GeneratingProvider> {
            return super.createHtmlGeneratingProviders(linkMap, baseURI) + mapOf(
                MarkdownElementTypes.MARKDOWN_FILE to object : GeneratingProvider {
                    override fun processNode(
                        visitor: HtmlGenerator.HtmlGeneratingVisitor,
                        text: String,
                        node: ASTNode
                    ) {
                        // don't wrap in body tag
                        node.acceptChildren(visitor)
                    }
                },
                MarkdownElementTypes.PARAGRAPH to object : GeneratingProvider {
                    override fun processNode(
                        visitor: HtmlGenerator.HtmlGeneratingVisitor,
                        text: String,
                        node: ASTNode
                    ) {
                        for (child in node.children) {
                            if (child is LeafASTNode) {
                                visitor.visitLeaf(child)
                            } else {
                                child.accept(visitor)
                            }
                        }

                        visitor.consumeHtml("<br />\n")
                    }
                }
            )
        }
    }
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(this)
    var html = HtmlGenerator(this, parsedTree, flavour).generateHtml()
    if (html.endsWith("<br />\n")) {
        html = html.substring(0, html.length - "<br />\n".length)
    }
    return html
}

private fun KtExpression.asString(): String? {
    if (this !is KtStringTemplateExpression) return null
    if (this.children.asSequence().any { it !is KtLiteralStringTemplateEntry }) return null
    return this.children.joinToString { (it as KtLiteralStringTemplateEntry).text }
}

internal fun KtClass.toClassDoc(): ClassDoc {
    val ktClass = this
    val className = ktClass.fqName!!.asString()
    val classComment = ktClass.docComment?.asString()
    val classTags = tags(ktClass.deprecated(), ktClass.docComment?.since())
    val properties = ktClass.getProperties().map { property ->
        val propertyName = property.name!!
        val propertyDoc = property.docComment?.asString()
        val propertyTags = tags(property.deprecated(), property.docComment?.since())
        return@map PropertyDoc(name = propertyName, comment = propertyDoc, tags = propertyTags)
    }
    return ClassDoc(
        fullyQualifiedName = className,
        comment = classComment,
        properties = properties,
        tags = classTags
    )
}
