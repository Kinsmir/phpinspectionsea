package com.kalessil.phpStorm.phpInspectionsEA.inspectors.semanticalAnalysis.classes;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.psi.elements.*;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import com.kalessil.phpStorm.phpInspectionsEA.utils.ExpressionSemanticUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.NamedElementUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class SenselessMethodDuplicationInspector extends BasePhpInspection {
    // configuration flags automatically saved by IDE
    @SuppressWarnings("WeakerAccess")
    public int MAX_METHOD_SIZE = 20;
    /* TODO: configurable via drop-down; clean code: 20 lines/method; PMD: 50; Checkstyle: 100 */

    private static final String messagePattern = "'%s%' method can be dropped, as it identical to parent's one.";

    @NotNull
    public String getShortName() {
        return "SenselessMethodDuplicationInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpMethod(Method method) {
                /* process non-test and reportable classes only */
                final PhpClass clazz        = method.getContainingClass();
                final PsiElement methodName = NamedElementUtil.getNameIdentifier(method);
                final GroupStatement body   = ExpressionSemanticUtil.getGroupStatement(method);
                if (null == methodName || null == body || null == clazz) {
                    return;
                }
                /* process only real classes and methods */
                if (method.isDeprecated() || clazz.isTrait() || clazz.isInterface() || method.isAbstract()) {
                    return;
                }

                /* don't take too heavy work */
                final int countExpressions = ExpressionSemanticUtil.countExpressionsInGroup(body);
                if (0 == countExpressions || countExpressions > MAX_METHOD_SIZE) {
                    return;
                }

                /* ensure parent, parent methods are existing and contains the same amount of expressions */
                final PhpClass parent           = clazz.getSuperClass();
                final Method parentMethod       = null == parent ? null : parent.findMethodByName(method.getName());
                final GroupStatement parentBody = null == parentMethod ? null : ExpressionSemanticUtil.getGroupStatement(parentMethod);
                if (null == parentBody || countExpressions != ExpressionSemanticUtil.countExpressionsInGroup(parentBody)) {
                    return;
                }

                /* iterate and compare expressions */
                PhpPsiElement ownExpression    = body.getFirstPsiChild();
                PhpPsiElement parentExpression = parentBody.getFirstPsiChild();
                for (int index = 0; index <= countExpressions; ++index) {
                    /* skip doc-blocks */
                    while (ownExpression instanceof PhpDocComment) {
                        ownExpression = ownExpression.getNextPsiSibling();
                    }
                    while (parentExpression instanceof PhpDocComment) {
                        parentExpression = parentExpression.getNextPsiSibling();
                    }
                    if (null == ownExpression || null == parentExpression) {
                        break;
                    }

                    /* process comparing 2 nodes */
                    if (!PsiEquivalenceUtil.areElementsEquivalent(ownExpression, parentExpression)) {
                        boolean mismatched = true;
                        /* PsiEquivalenceUtil.areElementsEquivalent is not handling assignments properly */
                        /* FIXME: ugly workaround / https://youtrack.jetbrains.com/issue/WI-34368 */
                        if (ownExpression.getTextLength() == parentExpression.getTextLength()) {
                            mismatched = !ownExpression.getText().equals(parentExpression.getText());
                        }

                        if (mismatched) {
                            return;
                        }
                    }
                    ownExpression    = ownExpression.getNextPsiSibling();
                    parentExpression = parentExpression.getNextPsiSibling();
                }


                /* methods seems to be identical: resolve used classes to avoid ns/imports magic */
                final Collection<String> collection = getUsedReferences(body);
                if (!collection.containsAll(getUsedReferences(parentBody))) {
                    collection.clear();
                    return;
                }
                collection.clear();


                final String message = messagePattern.replace("%s%", method.getName());
                holder.registerProblem(methodName, message, ProblemHighlightType.WEAK_WARNING);
            }

            private Collection<String> getUsedReferences(@NotNull GroupStatement body) {
                final Collection<PhpReference> references = PsiTreeUtil.findChildrenOfAnyType(
                        body, ClassReference.class, ConstantReference.class, FunctionReference.class);

                final Set<String> fqns = new HashSet<>(references.size());
                for (PhpReference reference : references) {
                    if (reference instanceof MethodReference) {
                        continue;
                    }

                    final PsiElement entry = reference.resolve();
                    if (entry instanceof PhpNamedElement) {
                        fqns.add(((PhpNamedElement) entry).getFQN());
                    }
                }
                references.clear();

                return fqns;
            }
        };
    }
}
