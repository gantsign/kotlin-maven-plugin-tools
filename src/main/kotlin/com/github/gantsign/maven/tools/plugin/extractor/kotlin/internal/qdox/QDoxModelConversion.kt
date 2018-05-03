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
package com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.qdox

import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.ClassDoc
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.DocTag
import com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model.PropertyDoc
import com.thoughtworks.qdox.model.DocletTag
import com.thoughtworks.qdox.model.JavaClass
import com.thoughtworks.qdox.model.JavaField

internal fun Map<String, JavaClass>.toClassDocMap(): Map<String, ClassDoc> {
    val classDocs = values.asSequence()
        .map { javaClass ->
            ClassDoc(
                fullyQualifiedName = javaClass.fullyQualifiedName,
                comment = javaClass.comment,
                properties = javaClass.fields.toPropertyDocs(),
                tags = javaClass.tags.toDocTags()
            )
        }
        .associateBy(ClassDoc::fullyQualifiedName)

    val superClassMapping = values
        .associate { it.fullyQualifiedName!! to it.superJavaClass?.fullyQualifiedName!! }

    for (classDoc in classDocs.values) {
        classDoc.superClassDoc = classDocs[superClassMapping[classDoc.fullyQualifiedName]]
    }

    return classDocs
}

private fun List<JavaField>.toPropertyDocs(): List<PropertyDoc> =
    map { javaField ->
        PropertyDoc(
            name = javaField.name,
            comment = javaField.comment,
            tags = javaField.tags.toDocTags()
        )
    }

private fun List<DocletTag>.toDocTags(): Map<String, DocTag> =
    associate {
        it.name to DocTag(
            it.value
        )
    }
