/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.stubindex

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.intellij.openapi.util.Key
import com.intellij.psi.stubs.IndexSink
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.stubs.KotlinCallableStubBase

fun indexTopLevelExtension<TDeclaration : JetCallableDeclaration>(stub: KotlinCallableStubBase<TDeclaration>, sink: IndexSink) {
    if (stub.isExtension()) {
        val declaration = stub.getPsi()
        declaration.getReceiverTypeReference()!!.getTypeElement()?.index(declaration, sink)
    }
}

private fun <TDeclaration : JetCallableDeclaration> JetTypeElement.index(declaration: TDeclaration, sink: IndexSink) {
    fun occurrence(typeName: String) {
        val name = declaration.getName() ?: return
        sink.occurrence(JetTopLevelExtensionsByReceiverTypeIndex.INSTANCE.getKey(),
                        JetTopLevelExtensionsByReceiverTypeIndex.buildKey(typeName, name))
    }

    when (this) {
        is JetUserType -> {
            var referenceName = getReferencedName() ?: return

            if (declaration is JetNamedFunction) {
                val typeParameter = declaration.getTypeParameters().firstOrNull { it.getName() == referenceName }
                if (typeParameter != null) {
                    val bound = typeParameter.getExtendsBound()
                    if (bound != null) {
                        bound.getTypeElement()?.index(declaration, sink)
                        return
                    }
                    occurrence("Any")
                    return
                }
            }

            occurrence(referenceName)

            val aliasNames = declaration.getContainingJetFile().aliasImportMap()[referenceName]
            aliasNames.forEach { occurrence(it) }
        }

        is JetNullableType -> getInnerType()?.index(declaration, sink)

        is JetFunctionType -> {
            val typeName = (if (getReceiverTypeReference() != null) "ExtensionFunction" else "Function") + getParameters().size()
            occurrence(typeName)
        }

        else -> occurrence("Any")
    }
}

private class CachedAliasImportData(val map: Multimap<String, String>, val fileModificationStamp: Long)

private val ALIAS_IMPORT_DATA_KEY = Key<CachedAliasImportData>("ALIAS_IMPORT_MAP_KEY")

private fun JetFile.aliasImportMap(): Multimap<String, String> {
    val cached = getUserData(ALIAS_IMPORT_DATA_KEY)
    val modificationStamp = getModificationStamp()
    if (cached != null && modificationStamp == cached.fileModificationStamp) {
        return cached.map
    }

    val data = CachedAliasImportData(buildAliasImportMap(), modificationStamp)
    putUserData(ALIAS_IMPORT_DATA_KEY, cached)
    return data.map
}

private fun JetFile.buildAliasImportMap(): Multimap<String, String> {
    val map = HashMultimap.create<String, String>()
    val importList = getImportList() ?: return map
    for (import in importList.getImports()) {
        val aliasName = import.getAliasName() ?: continue
        val name = import.getImportPath()?.fqnPart()?.shortName()?.asString() ?: continue
        map.put(aliasName, name)
    }
    return map
}
