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

import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.ClassDoc
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.DocTag
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.PropertyDoc
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.dokka.ContentNode
import org.jetbrains.dokka.DocumentationModule
import org.jetbrains.dokka.DocumentationNode
import org.jetbrains.dokka.NodeKind
import org.jetbrains.dokka.qualifiedName

internal fun DocumentationModule.toClassDocs(): Map<String, ClassDoc> {
    val packages = members

    val superClassMap = packages
        .flatMap { it.members(NodeKind.Class) }
        .associate { it.qualifiedName() to it.detailOrNull(NodeKind.Supertype)?.links?.firstOrNull()?.qualifiedName() }

    val classDocs = packages
        .flatMap { it.members(NodeKind.Class) }
        .map(DocumentationNode::toClassDoc)
        .associateBy(ClassDoc::fullyQualifiedName)

    for (classDoc in classDocs.values) {
        classDoc.superClassDoc = classDocs[superClassMap[classDoc.fullyQualifiedName]]
    }

    return classDocs
}

private fun DocumentationNode.toClassDoc(): ClassDoc {
    var tags: Map<String, DocTag> = emptyMap()

    deprecationTag()?.let { tags += mapOf("deprecated" to it) }
    sinceTag()?.let { tags += mapOf("since" to it) }

    return ClassDoc(
        fullyQualifiedName = qualifiedName(),
        //            superClass = node.detailOrNull(NodeKind.Supertype)?.links?.firstOrNull()?.qualifiedName(),
        comment = description(),
        properties = this.members(org.jetbrains.dokka.NodeKind.Property).map(DocumentationNode::toPropertyDoc),
        tags = tags
    )
}

private fun DocumentationNode.toPropertyDoc(): PropertyDoc {
    var tags: Map<String, DocTag> = emptyMap()

    deprecationTag()?.let { tags += mapOf("deprecated" to it) }
    sinceTag()?.let { tags += mapOf("since" to it) }

    return PropertyDoc(
        name = name,
        comment = description(),
        tags = tags
    )
}

private fun DocumentationNode.sinceTag(): DocTag? =
    content.sections.firstOrNull { it.tag == "Since" }?.let {
        DocTag(it.children.asText())
    }

private fun DocumentationNode.deprecationTag(): DocTag? =
    deprecation?.run {
        when (kind) {
            NodeKind.Modifier -> DocTag(content.children.asText())
            NodeKind.Annotation ->
                DocTag(
                    details(NodeKind.Parameter)
                        .firstOrNull { it.name == "message" }
                        ?.detailOrNull(NodeKind.Value)?.name
                        ?.let { StringUtil.unescapeStringCharacters(StringUtil.unquoteString(it)) }
                )
            else -> DocTag()
        }
    }

private fun Iterable<ContentNode>.asText(): String? {
    val content = toList()
    if (content.isEmpty()) {
        return null
    }
    val sb = HtmlStringBuilder()
    sb.appendContent(content)
    return sb.toString()
}

private fun DocumentationNode.description(): String? =
    content.children.asText()
