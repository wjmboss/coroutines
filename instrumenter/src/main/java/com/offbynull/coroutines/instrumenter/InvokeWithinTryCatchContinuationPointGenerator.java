/*
 * Copyright (c) 2016, Kasra Faghihi, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.offbynull.coroutines.instrumenter;

import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.loadLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.loadOperandStackPrefix;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.loadOperandStackSuffix;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.popMethodResult;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.returnDummy;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.saveLocalVariableTable;
import static com.offbynull.coroutines.instrumenter.ContinuationInstructionGenerationUtils.saveOperandStack;
import static com.offbynull.coroutines.instrumenter.ContinuationPointInstructionUtils.castToObjectAndSave;
import static com.offbynull.coroutines.instrumenter.ContinuationPointInstructionUtils.loadAndCastToOriginal;
import static com.offbynull.coroutines.instrumenter.ContinuationPointInstructionUtils.throwThrowableInVariable;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.addLabel;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.call;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.cloneInvokeNode;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.construct;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.empty;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.ifIntegersEqual;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.jumpTo;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.lineNumber;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.loadIntConst;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.loadVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.merge;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.saveVar;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.tryCatchBlock;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import static com.offbynull.coroutines.user.Continuation.MODE_SAVING;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.combineObjectArrays;
import static com.offbynull.coroutines.instrumenter.asm.MethodInvokeUtils.getArgumentCountRequiredForInvocation;
import static com.offbynull.coroutines.instrumenter.asm.MethodInvokeUtils.getReturnTypeOfInvocation;
import static com.offbynull.coroutines.instrumenter.asm.InstructionGenerationUtils.cloneInsnList;

final class InvokeWithinTryCatchContinuationPointGenerator extends ContinuationPointGenerator {

    public InvokeWithinTryCatchContinuationPointGenerator(
            int id,
            AbstractInsnNode invokeInsnNode,
            LineNumberNode invokeLineNumberNode,
            Frame<BasicValue> frame,
            Type returnType,
            FlowInstrumentationVariables flowInstrumentationVariables,
            MonitorInstrumentationInstructions monitorInstrumentationInstructions) {
        super(id, invokeInsnNode, invokeLineNumberNode, frame, returnType, flowInstrumentationVariables,
                monitorInstrumentationInstructions);
    }
    
    
    @Override
    ContinuationPointInstructions generate() {
        LabelNode continueExecLabelNode = new LabelNode();
        LabelNode failedRestoreExecLabelNode = new LabelNode();
        List<TryCatchBlockNode> tryCatchBlockNodes = new ArrayList<>();
    
        return new ContinuationPointInstructions(
                getInvokeInsnNode(),
                generateLoadInstructions(continueExecLabelNode, failedRestoreExecLabelNode, tryCatchBlockNodes),
                generateInvokeReplacementInstructions(continueExecLabelNode, failedRestoreExecLabelNode),
                tryCatchBlockNodes);
    }
    
    private InsnList generateLoadInstructions(
            LabelNode continueExecLabelNode,
            LabelNode failedRestoreExecLabelNode,
            List<TryCatchBlockNode> tryCatchBlockNodes) {
        // tryCatchBlock() invocation further on in this method will populate TryCatchBlockNode fields
        TryCatchBlockNode newTryCatchBlockNode = new TryCatchBlockNode(null, null, null, null);
        tryCatchBlockNodes.add(newTryCatchBlockNode);
        
        FlowInstrumentationVariables vars = getFlowInstrumentationVariables();
        MonitorInstrumentationInstructions monInsts = getMonitorInstrumentationInstructions();
        
        Variable contArg = vars.getContArg();
        Variable methodStateVar = vars.getMethodStateVar();
        Variable savedLocalsVar = vars.getSavedLocalsVar();
        Variable savedStackVar = vars.getSavedStackVar();
        Variable tempObjVar2 = vars.getTempObjVar2();
        
        InsnList enterMonitorsInLockStateInsnList = monInsts.getEnterMonitorsInLockStateInsnList();
        InsnList exitMonitorsInLockStateInsnList = monInsts.getExitMonitorsInLockStateInsnList();
        
        Type invokeMethodReturnType = getReturnTypeOfInvocation(getInvokeInsnNode());
        Type returnType = getReturnType();
        int methodStackCount = getArgumentCountRequiredForInvocation(getInvokeInsnNode());
        Integer lineNum = getLineNumber();
        
        Frame<BasicValue> frame = getFrame();
        
        //          enterLocks(lockState);
        //          continuation.addPending(methodState); // method state should be loaded from Continuation.saved
        //              // Load up enough of the stack to invoke the method. The invocation here needs to be wrapped in a try catch because
        //              // the original invocation was within a try catch block (at least 1, maybe more). If we do get a throwable, jump
        //              // back to the area where the original invocation was and rethrow it there so the proper catch handlers can
        //              // handle it (if the handler is for the expected throwable type).
        //          restoreStackSuffix(stack, <number of items required for method invocation below>);
        //          try {
        //              <method invocation>
        //          } catch (throwable) {
        //              tempObjVar2 = throwable;
        //              restoreOperandStack(stack);
        //              restoreLocalsStack(localVars);
        //              goto restorePoint_<number>_rethrow;
        //          }
        //          if (continuation.getMode() == MODE_SAVING) {
        //              exitLocks(lockState);
        //              return <dummy>;
        //          }
        //             // At this point the invocation happened successfully, so we want to save the invocation's result, restore this
        //             // method's state, and then put the result on top of the stack as if invocation just happened. We then jump in to
        //             // the method and continue running it from the instruction after the original invocation point.
        //          tempObjVar2 = <method invocation>'s return value; // does nothing if ret type is void
        //          restoreOperandStack(stack);
        //          restoreLocalsStack(localVars);
        //          place tempObjVar2 on top of stack if not void (as if it <method invocation> were just run and returned that value)
        //          goto restorePoint_<number>_continue;
        
        return merge(lineNum == null ? empty() : lineNumber(lineNum),
                cloneInsnList(enterMonitorsInLockStateInsnList),
                loadOperandStackSuffix(savedStackVar, frame, methodStackCount),
                tryCatchBlock(
                        newTryCatchBlockNode,
                        null,
                        merge(
                                cloneInvokeNode(getInvokeInsnNode()) // invoke method
                        ),
                        merge(
                                saveVar(tempObjVar2),
                                loadOperandStackPrefix(savedStackVar, frame, frame.getStackSize() - methodStackCount),
                                loadLocalVariableTable(savedLocalsVar, frame),
                                jumpTo(failedRestoreExecLabelNode)
                        )
                ),
                ifIntegersEqual(// if we're saving after invoke, return dummy value
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(
                                popMethodResult(getInvokeInsnNode()),
                                cloneInsnList(exitMonitorsInLockStateInsnList), // inserted many times, must be cloned
                                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg), loadVar(methodStateVar)),
                                returnDummy(returnType)
                        )
                ),
                castToObjectAndSave(invokeMethodReturnType, tempObjVar2), // save return (does nothing if invoked method returns void)
                loadOperandStackPrefix(savedStackVar, frame, frame.getStackSize() - methodStackCount),
                loadLocalVariableTable(savedLocalsVar, frame),
                loadAndCastToOriginal(invokeMethodReturnType, tempObjVar2),
                jumpTo(continueExecLabelNode)
        );
    }

    private InsnList generateInvokeReplacementInstructions(
            LabelNode continueExecLabelNode,
            LabelNode failedRestoreExecLabelNode) {
        FlowInstrumentationVariables vars = getFlowInstrumentationVariables();
        MonitorInstrumentationInstructions monInsts = getMonitorInstrumentationInstructions();
        
        Variable contArg = vars.getContArg();
        Variable savedLocalsVar = vars.getSavedLocalsVar();
        Variable savedStackVar = vars.getSavedStackVar();
        Variable savedPartialStackVar = vars.getSavedPartialStackVar();
        Variable savedArgsVar = vars.getSavedArgumentsVar();
        Variable tempObjVar2 = vars.getTempObjVar2();
        
        InsnList loadLockStateToStackInsnList = monInsts.getLoadLockStateToStackInsnList();
        InsnList exitMonitorsInLockStateInsnList = monInsts.getExitMonitorsInLockStateInsnList();
        
        Type returnType = getReturnType();
        
        Frame<BasicValue> frame = getFrame();
        
        //          Object[] stack = saveOperandStack();
        //          Object[] locals = saveLocals();
        //          continuation.addPending(new MethodState(<number>, stack, locals, lockState);
        //          <method invocation>
        //          if (continuation.getMode() == MODE_SAVING) {
        //              exitLocks(lockState);
        //              return <dummy>;
        //          }
        //          goto restorePoint_<number>_continue;
        //
        //          restorePoint_<number>_rethrow:
        //          throw tempObjVar2; // The exception we got during the loading phase has to be thrown here, because that exception may
        //                             // need to be handled by exception handlers surrounding the original invocation.
        //
        //          restorePoint_<number>_continue:
        int stackCountForMethodInvocation = getArgumentCountRequiredForInvocation(getInvokeInsnNode());
        int preInvokeStackSize = frame.getStackSize();
        int postInvokeStackSize = frame.getStackSize() - stackCountForMethodInvocation;
        
        return merge(
                // save args for invoke
                saveOperandStack(savedArgsVar, frame, preInvokeStackSize, stackCountForMethodInvocation),
                cloneInvokeNode(getInvokeInsnNode()), // invoke method
                ifIntegersEqual(// if we're saving after invoke, return dummy value
                        call(CONTINUATION_GETMODE_METHOD, loadVar(contArg)),
                        loadIntConst(MODE_SAVING),
                        merge(
                                popMethodResult(getInvokeInsnNode()),
                                // since we invoked the method before getting here, we already consumed the arguments that were sitting
                                // on the stack waiting to be consumed by the method -- as such, subtract the number of arguments from the
                                // total stack size when saving!!!!! THIS IS SUPER IMPORTANT!!!!
                                saveOperandStack(savedPartialStackVar, frame, postInvokeStackSize, postInvokeStackSize),
                                combineObjectArrays(savedStackVar, savedPartialStackVar, savedArgsVar),
                                saveLocalVariableTable(savedLocalsVar, frame),
                                cloneInsnList(exitMonitorsInLockStateInsnList), // inserted many times, must be cloned
                                call(CONTINUATION_ADDPENDING_METHOD, loadVar(contArg),
                                        construct(METHODSTATE_INIT_METHOD,
                                                loadIntConst(getId()),
                                                loadVar(savedStackVar),
                                                loadVar(savedLocalsVar),
                                                cloneInsnList(loadLockStateToStackInsnList) // inserted many times, must be cloned
                                        )
                                ),
                                returnDummy(returnType)
                        )
                ),
                jumpTo(continueExecLabelNode),
                
                
                
                addLabel(failedRestoreExecLabelNode),
                throwThrowableInVariable(tempObjVar2),
                
                
                
                addLabel(continueExecLabelNode)
        );
    }
    
}
