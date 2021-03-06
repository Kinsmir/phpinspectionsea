package com.kalessil.phpStorm.phpInspectionsEA.inspectors.apiUsage.arrays;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import org.jetbrains.annotations.NotNull;

public class LowPerformanceArrayUniqueUsageInspector extends BasePhpInspection {
    private static final String strProblemUseArrayKeysWithCountValues = "'array_keys(array_count_values(...))' would be more efficient (make sure to leave a comment to explain the intent).";
    private static final String strProblemUseCountWithCountValues     = "'count(array_count_values(...))' would be more efficient (make sure to leave a comment to explain the intent).";

    @NotNull
    public String getShortName() {
        return "LowPerformanceArrayUniqueUsageInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpFunctionCall(FunctionReference reference) {
                /* try filtering by args count first */
                final PsiElement[] parameters = reference.getParameters();
                final String strFunctionName  = reference.getName();
                if (parameters.length != 1 || StringUtil.isEmpty(strFunctionName) || !strFunctionName.equals("array_unique")) {
                    return;
                }

                /* check it's nested call */
                if (reference.getParent() instanceof ParameterList) {
                    ParameterList params = (ParameterList) reference.getParent();
                    if (params.getParent() instanceof FunctionReference) {
                        FunctionReference parentCall = (FunctionReference) params.getParent();
                        /* look up for parent function name */
                        String strParentFunction = parentCall.getName();
                        if (! StringUtil.isEmpty(strParentFunction)) {

                            /* === test array_values(array_unique(<expression>)) case === */
                            if (strParentFunction.equals("array_values")) {
                                holder.registerProblem(parentCall, strProblemUseArrayKeysWithCountValues, ProblemHighlightType.WEAK_WARNING);
                                return;
                            }

                            /* === test count(array_unique(<expression>)) case === */
                            if (strParentFunction.equals("count")) {
                                holder.registerProblem(parentCall, strProblemUseCountWithCountValues, ProblemHighlightType.WEAK_WARNING);
                                // return;
                            }
                        }
                    }
                }
            }

        };
    }
}
