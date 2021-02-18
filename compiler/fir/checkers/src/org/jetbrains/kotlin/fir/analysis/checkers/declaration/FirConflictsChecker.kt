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
    override fun check(declaration: FirDeclaration, context: CheckerContext, reporter: DiagnosticReporter) {
        val inspector = FirDeclarationInspector()

        when (declaration) {
            is FirFile -> checkFile(declaration, inspector, context)
            is FirRegularClass -> checkRegularClass(declaration, inspector)
            else -> return
        }

        inspector.functionDeclarations.forEachNonSingle { it, symbols ->
            reporter.reportOn(it.source, FirErrors.CONFLICTING_OVERLOADS, symbols, context)
        }

        inspector.otherDeclarations.forEachNonSingle { it, symbols ->
            reporter.reportOn(it.source, FirErrors.REDECLARATION, symbols, context)
        }
    }

    private fun Map<String, LinkedHashSet<*>>.forEachNonSingle(action: (FirDeclaration, Collection<AbstractFirBasedSymbol<*>>) -> Unit) {
        for (value in values) {
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
