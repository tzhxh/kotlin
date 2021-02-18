/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.fir.FirSymbolOwner
import org.jetbrains.kotlin.fir.analysis.checkers.FirDeclarationInspector
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.firLookupTracker
import org.jetbrains.kotlin.fir.scopes.PACKAGE_MEMBER
import org.jetbrains.kotlin.fir.scopes.impl.FirPackageMemberScope
import org.jetbrains.kotlin.fir.symbols.AbstractFirBasedSymbol
import org.jetbrains.kotlin.fir.visibilityChecker
import org.jetbrains.kotlin.name.Name
import java.util.LinkedHashSet

object FirConflictsChecker : FirBasicDeclarationChecker() {

    private class DeclarationInspector : FirDeclarationInspector() {

        val otherActualDeclarations = mutableMapOf<String, LinkedHashSet<FirDeclaration>>()
        val functionActualDeclarations = mutableMapOf<String, LinkedHashSet<FirSimpleFunction>>()
        val otherExpectDeclarations = mutableMapOf<String, LinkedHashSet<FirDeclaration>>()
        val functionExpectDeclarations = mutableMapOf<String, LinkedHashSet<FirSimpleFunction>>()

        override fun collectNonFunctionDeclaration(declaration: FirDeclaration) {
            val target = when {
                declaration is FirMemberDeclaration && declaration.status.isExpect -> otherExpectDeclarations
                declaration is FirMemberDeclaration && declaration.status.isActual -> otherActualDeclarations
                else -> otherDeclarations
            }
            val key = when (declaration) {
                is FirRegularClass -> presenter.represent(declaration)
                is FirTypeAlias -> presenter.represent(declaration)
                is FirProperty -> presenter.represent(declaration)
                else -> return
            }
            collectNonFunctionDeclaration(target, key, declaration)
        }

        override fun collectFunction(declaration: FirSimpleFunction) {
            val target = when {
                declaration.status.isExpect -> functionExpectDeclarations
                declaration.status.isActual -> functionActualDeclarations
                else -> functionDeclarations
            }
            collectFunction(target, presenter.represent(declaration), declaration)
        }
    }

    override fun check(declaration: FirDeclaration, context: CheckerContext, reporter: DiagnosticReporter) {
        val inspector = DeclarationInspector()

        when (declaration) {
            is FirFile -> checkFile(declaration, inspector, context)
            is FirRegularClass -> checkRegularClass(declaration, inspector)
            else -> return
        }

        fun reportConflictingOverload(declaration: FirDeclaration, symbols: Collection<AbstractFirBasedSymbol<*>>) {
            reporter.reportOn(declaration.source, FirErrors.CONFLICTING_OVERLOADS, symbols, context)
        }

        fun reportRedeclaration(declaration: FirDeclaration, symbols: Collection<AbstractFirBasedSymbol<*>>) {
            reporter.reportOn(declaration.source, FirErrors.REDECLARATION, symbols, context)
        }

        inspector.functionDeclarations.forEachNonSingle(emptyMap(), ::reportConflictingOverload)
        inspector.functionExpectDeclarations.forEachNonSingle(inspector.functionDeclarations, ::reportConflictingOverload)
        inspector.functionActualDeclarations.forEachNonSingle(inspector.functionDeclarations, ::reportConflictingOverload)

        inspector.otherDeclarations.forEachNonSingle(emptyMap(), ::reportRedeclaration)
        inspector.otherExpectDeclarations.forEachNonSingle(inspector.otherDeclarations, ::reportRedeclaration)
        inspector.otherActualDeclarations.forEachNonSingle(inspector.otherDeclarations, ::reportRedeclaration)
    }

    private fun Map<String, LinkedHashSet<*>>.forEachNonSingle(
        additionalDeclarations: Map<String, LinkedHashSet<*>>,
        action: (FirDeclaration, Collection<AbstractFirBasedSymbol<*>>) -> Unit
    ) {
        for (entry in entries) {
            val additionalValue = additionalDeclarations[entry.key]
            val value = if (additionalValue?.isEmpty() == false) entry.value + additionalValue else entry.value
            if (value.size > 1) {
                val symbols = value.mapNotNull { (it as? FirSymbolOwner<*>)?.symbol }

                value.forEach {
                    action(it as FirDeclaration, symbols)
                }
            }
        }
    }

    private fun checkFile(file: FirFile, inspector: FirDeclarationInspector, context: CheckerContext) {
        val lookupTracker = context.session.firLookupTracker

        val packageMemberScope: FirPackageMemberScope = context.sessionHolder.scopeSession.getOrBuild(file.packageFqName, PACKAGE_MEMBER) {
            FirPackageMemberScope(file.packageFqName, context.sessionHolder.session)
        }
        val visibilityChecker = context.session.visibilityChecker

        fun collectClassLikeConflicts(declarationName: Name) {
            packageMemberScope.processClassifiersByNameWithSubstitution(declarationName) { symbol, _ ->
                (symbol.fir as? FirDeclaration)?.let { declaration ->
                    if (declaration !is FirMemberDeclaration ||
                        (declaration is FirSymbolOwner<*> &&
                                visibilityChecker.isVisible(declaration, context.session, file, emptyList(), dispatchReceiver = null))
                    ) {
                        inspector.collect(declaration)
                    }
                }
            }
        }

        for (topLevelDeclaration in file.declarations) {
            inspector.collect(topLevelDeclaration)
            var declarationName: Name? = null
            when (topLevelDeclaration) {
                is FirSimpleFunction -> {
                    declarationName = topLevelDeclaration.name
                    packageMemberScope.processFunctionsByName(declarationName) {
                        if (visibilityChecker.isVisible(it.fir, context.session, file, emptyList(), dispatchReceiver = null)) {
                            inspector.collect(it.fir)
                        }
                    }
                }
                is FirVariable<*> -> {
                    declarationName = topLevelDeclaration.name
                    packageMemberScope.processPropertiesByName(declarationName) {
                        val declaration = it.fir
                        if (declaration !is FirMemberDeclaration ||
                            visibilityChecker.isVisible(declaration, context.session, file, emptyList(), dispatchReceiver = null)
                        ) {
                            inspector.collect(it.fir)
                        }
                    }
                }
                is FirRegularClass -> {
                    declarationName = topLevelDeclaration.name
                    collectClassLikeConflicts(declarationName)
                }
                is FirTypeAlias -> {
                    declarationName = topLevelDeclaration.name
                    collectClassLikeConflicts(declarationName)
                }
            }
            if (lookupTracker != null && declarationName != null) {
                lookupTracker.recordLookup(declarationName, topLevelDeclaration.source, file.source, file.packageFqName.asString())
            }
        }
    }

    private fun checkRegularClass(declaration: FirRegularClass, inspector: FirDeclarationInspector) {
        for (it in declaration.declarations) {
            inspector.collect(it)
        }
    }
}
