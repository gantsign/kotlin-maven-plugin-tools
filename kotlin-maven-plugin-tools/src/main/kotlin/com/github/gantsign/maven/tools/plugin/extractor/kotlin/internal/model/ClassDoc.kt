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
package com.github.gantsign.maven.tools.plugin.extractor.kotlin.internal.model

internal data class ClassDoc(
    val fullyQualifiedName: String,
    val comment: String?,
    val properties: List<PropertyDoc>,
    val tags: Map<String, DocTag>
) {
    var superClassDoc: ClassDoc? = null
        set(value) {
            if (field != null) throw IllegalStateException("superClassDoc cannot be changed once set")
            field = value
        }

    override fun equals(other: Any?): Boolean {
        if (other !is ClassDoc) {
            return false
        }
        return fullyQualifiedName == other.fullyQualifiedName
    }

    override fun hashCode(): Int = fullyQualifiedName.hashCode()
}
