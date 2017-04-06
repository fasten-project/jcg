/*
 * BSD 2-Clause License:
 * Copyright (c) 2009 - 2016
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 */
package app;

import static lib.annotations.callgraph.CallGraphAlgorithm.CHA;
import static lib.annotations.documentation.CGCategory.*;
import static java.lang.Integer.parseInt;

import lib.annotations.callgraph.*;
import lib.annotations.documentation.CGNote;
import lib.annotations.properties.EntryPoint;

import static lib.annotations.callgraph.AnalysisMode.*;
import static lib.UnaryOperator.*;

import lib.*;

import java.util.Arrays;

import static lib.BinaryExpression.createBinaryExpression;
import static lib.PlusOperator.AddExpression;
import static lib.testutils.CallbackTest.callback;

/**
 * This class defines an application use case of the expression library and has some well defined properties
 * wrt. call graph construction. It covers ( inlc. the library) serveral Java language features to test whether
 * a given call graph implementation can handle these features.
 * <p>
 * <p>
 * <b>NOTE</b><br>
 * This class is not meant to be (automatically) recompiled; it just serves documentation
 * purposes.
 * <p>
 * <!--
 * <p>
 * <p>
 * <p>
 * <p>
 * INTENTIONALLY LEFT EMPTY TO MAKE SURE THAT THE SPECIFIED LINE NUMBERS ARE STABLE IF THE
 * CODE (E.G. IMPORTS) CHANGE.
 * <p>
 * <p>
 * 
 * -->
 *
 * @author Michael Eichberg
 * @author Micahel Reif
 * @author Roberts Kolosovs
 */
public class ExpressionEvaluator {

    private static final Map<String, Constant> ENV = new Map<String, Constant>();
    static {ENV.add("x", new Constant(1));}
    // 2 34 + 23 Plus == 59
    //20 20 + 1 Plus ++ Id == 42
    // 2 3 + 5 Plus 2 fancy_expressions.MultOperator
    // 2 3 + 5 Plus 2 fancy_expressions.MultOperator Crash
    @EntryPoint(value = {DESKTOP_APP, OPA, CPA})
    @InvokedConstructor(receiverType = "app/ExpressionEvaluator", line = 108)
    @InvokedConstructor(receiverType = "lib/PlusOperator", line = 109)
    @InvokedConstructor(receiverType = "lib/Stack", line = 152)
    @CallSite(name = "printSubtraction", resolvedMethods = {@ResolvedMethod(receiverType = "app/ExpressionEvaluator")}, line = 109) 
    @CallSite(name = "clone", resolvedMethods = {@ResolvedMethod(receiverType = "java/lang/Object")}, line = 111)
    @CallSite(name = "push", parameterTypes = {Constant.class}, resolvedMethods = {@ResolvedMethod(receiverType = "lib/Stack")}, line = 156)
    @CallSite(name = "eval", returnType = Constant.class, parameterTypes = {Map.class}, resolvedMethods = {@ResolvedMethod(receiverType = UnaryExpression.FQN)}, line = 164)
    @CallSite(name = "eval", returnType = Constant.class, parameterTypes = {Map.class},
            resolvedMethods = {
                    @ResolvedMethod(receiverType = UnaryExpression.FQN, iff = {@ResolvingCondition(containedInMax = CHA)}),
                    @ResolvedMethod(receiverType = SquareExpression.FQN)},
            line = 171)
    @CallSite(name = "createBinaryExpression",
            resolvedMethods = {@ResolvedMethod(receiverType = BinaryExpression.FQN)},
            parameterTypes = {String.class, Expression.class, Expression.class},
            line = 175)
    public static void main(final String[] args) {
 
    	ExpressionEvaluator mainClass = new ExpressionEvaluator();
    	mainClass.printSubtraction(new PlusOperator.AddExpression(null, null));

        String[] expressions = args.clone();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

            @CGNote(value = JVM_CALLBACK, description = "invisible callback because no native code is involved; the call graph seems to be complete")
            @CGNote(value = NOTE, description = "the related method <Thread>.dispatchUncaughtException is not dead")
            @CallSite(name = "callback", resolvedMethods = {@ResolvedMethod(receiverType = "lib/testutils/CallbackTest")}, line = 120)
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                callback();
                String msg = "unexpected error while processing " + Arrays.deepToString(args);
                System.out.println(msg);
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread() {

            // This is an entry point!
            @CGNote(value = JVM_CALLBACK, description = "invisible callback because no native code is involved; the call graph seems to be complete")
            @CGNote(value = NOTE, description = "the related method<Thread>.run is called by the jvm")
            @CallSite(name = "callback", resolvedMethods = {@ResolvedMethod(receiverType = "lib/testutils/CallbackTest")}, line = 134)
            @Override
            public void run() {
                callback();
                System.out.println("It was a pleasure to evaluate your expression!");
                super.run();
            }
        });

        synchronized (ExpressionEvaluator.class) {
            // all methods of the class object of ExpressionEvaluation may be called...
            // unless we analyze the "JVM" internal implementation
            @CGNote(value = NATIVE_CALLBACK, description = "potential callback because native code is involved; the call graph seems to be complete")
            @CGNote(value = NOTE, description = "the native code may call any of the methods declared at the class object of ExpressionEvaluation")
            boolean holdsLock = !Thread.holdsLock(ExpressionEvaluator.class);
            if (holdsLock) throw new UnknownError();

            if (args.length == 0) {
                throw new IllegalArgumentException("no expression");
            }

            Stack<Constant> values = new Stack();

            for (String expr : expressions) {
                try {
                    values.push(new Constant(parseInt(expr)));
                } catch (NumberFormatException nfe) {
                    // so it is not a number...
                    switch (expr) {
                        case "+":
                            values.push(new AddExpression(values.pop(), values.pop()).eval(ENV));
                            break;
                        case "++":
                            values.push(UnaryExpression.createUnaryExpressions(INCREMENT, values.pop()).eval(ENV));
                            break;
                        case "Id":
                            values.push(new IdentityExpression(values.pop()).eval(ENV));
                            break;
                        case "²":
                            UnaryExpression square = new SquareExpression(values.pop());
                            values.push(square.eval(ENV));
                            break;
                        default:
                            try {
                                values.push(createBinaryExpression(expr, values.pop(), values.pop()).eval(ENV));
                            } catch (Throwable t) {
                                throw new IllegalArgumentException("unsupported symbol " + expr, t);
                            }
                    }
                } finally {
                    System.out.println("processed the symbol " + expr);
                }
            }

            if (values.size() > 1) {
                throw new IllegalArgumentException("the expression is not valid missing operator");
            }

            System.out.print("result "); ExpressionPrinter.printExpression(values.pop()); System.out.print(" with environment " + ENV.toString());
        }
    }

    /*
     * !!!!! THIS METHOD IS NOT INTENDED TO BE CALLED DIRECTLY !!!!
     * The ExpressionEvaluator.class is passed to a native method with an ´Object´ type
     * as parameter. The native method can (potentially) call any visible method on the passed object, i.e. toString().
     */
    @CallSite(name = "callback", resolvedMethods = {@ResolvedMethod(receiverType = "lib/testutils/CallbackTest")}, line = 201)
    @EntryPoint(value = {OPA, CPA})
    public String toString() {
        callback();
        return "ExpressionEvaluater v0.1";
    }
    
    /*
     * This method contains calls which are not detected if this method is not labeled 
     * as an entry point and a context sensitive analysis is employed.
     */
    @CallSite(name = "printText", resolvedMethods = {
    		@ResolvedMethod(receiverType = "app/ExpressionEvaluator", 
    						iff = {@ResolvingCondition(mode = {OPA, CPA})})}, line = 215)
    @EntryPoint(value = {OPA, CPA})
    public void printSubtraction(Expression op){
    	if (op instanceof SubOperator.SubExpression) {
			printText(((SubOperator.SubExpression) op).left() + "-" + ((SubOperator.SubExpression) op).right());
		} else {
			//do nothing
		}
    }
    
    private void printText(String txt){
    	System.out.println(txt);
    }
}