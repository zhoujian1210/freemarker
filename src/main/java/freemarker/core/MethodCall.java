/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * 22 October 1999: This class added by Holger Arendt.
 */

package freemarker.core;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import freemarker.template.TemplateException;
import freemarker.template.TemplateMethodModel;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.utility.NullWriter;


/**
 * A unary operator that calls a TemplateMethodModel.  It associates with the
 * <tt>Identifier</tt> or <tt>Dot</tt> to its left.
 */
final class MethodCall extends Expression {

    private final Expression target;
    private final ListLiteral arguments;

    MethodCall(Expression target, ArrayList arguments) {
        this(target, new ListLiteral(arguments));
    }

    private MethodCall(Expression target, ListLiteral arguments) {
        this.target = target;
        this.arguments = arguments;
    }

    @Override
    TemplateModel _eval(Environment env) throws TemplateException {
        TemplateModel targetModel = target.eval(env);
        if (targetModel instanceof TemplateMethodModel) {
            TemplateMethodModel targetMethod = (TemplateMethodModel) targetModel;
            List argumentStrings = 
            targetMethod instanceof TemplateMethodModelEx
            ? arguments.getModelList(env)
            : arguments.getValueList(env);
            Object result = targetMethod.exec(argumentStrings);
            return env.getObjectWrapper().wrap(result);
        } else if (targetModel instanceof BoundCallable) {
            final BoundCallable boundFunc = (BoundCallable) targetModel;
            final Macro unboundFunc = boundFunc.getUnboundCallable();
            env.setLastReturnValue(null);
            if (!unboundFunc.isFunction()) {
                throw new _MiscTemplateException(env, "A macro cannot be called in an expression. (Functions can be.)");
            }
            Writer prevOut = env.getOut();
            try {
                env.setOut(NullWriter.INSTANCE);
                env.invoke(boundFunc, null, arguments.items, null, null);
            } catch (IOException e) {
                // Should not occur
                throw new TemplateException("Unexpected exception during function execution", e, env);
            } finally {
                env.setOut(prevOut);
            }
            return env.getLastReturnValue();
        } else {
            throw new NonMethodException(target, targetModel, env);
        }
    }

    @Override
    public String getCanonicalForm() {
        StringBuilder buf = new StringBuilder();
        buf.append(target.getCanonicalForm());
        buf.append("(");
        String list = arguments.getCanonicalForm();
        buf.append(list.substring(1, list.length() - 1));
        buf.append(")");
        return buf.toString();
    }

    @Override
    String getNodeTypeSymbol() {
        return "...(...)";
    }
    
    TemplateModel getConstantValue() {
        return null;
    }

    @Override
    boolean isLiteral() {
        return false;
    }

    @Override
    protected Expression deepCloneWithIdentifierReplaced_inner(
            String replacedIdentifier, Expression replacement, ReplacemenetState replacementState) {
        return new MethodCall(
                target.deepCloneWithIdentifierReplaced(replacedIdentifier, replacement, replacementState),
                (ListLiteral) arguments.deepCloneWithIdentifierReplaced(replacedIdentifier, replacement, replacementState));
    }

    @Override
    int getParameterCount() {
        return 1 + arguments.items.size();
    }

    @Override
    Object getParameterValue(int idx) {
        if (idx == 0) {
            return target;
        } else if (idx < getParameterCount()) {
            return arguments.items.get(idx - 1);
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    ParameterRole getParameterRole(int idx) {
        if (idx == 0) {
            return ParameterRole.CALLEE;
        } else if (idx < getParameterCount()) {
            return ParameterRole.ARGUMENT_VALUE;
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

}
