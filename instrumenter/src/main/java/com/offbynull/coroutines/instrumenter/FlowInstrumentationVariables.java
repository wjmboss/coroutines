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

import com.offbynull.coroutines.instrumenter.asm.VariableTable;
import com.offbynull.coroutines.instrumenter.asm.VariableTable.Variable;
import com.offbynull.coroutines.user.Continuation;
import com.offbynull.coroutines.user.MethodState;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.Type;

final class FlowInstrumentationVariables {

    private final Variable contArg;
    private final Variable methodStateVar;
    private final Variable savedLocalsVar;
    private final Variable savedStackVar;
    private final Variable savedArgumentsVar;
    private final Variable savedPartialStackVar;
    private final Variable returnValObjectVar;

    public FlowInstrumentationVariables(VariableTable varTable, Variable contArg, Variable methodStateVar) {
        Validate.notNull(varTable);
        Validate.notNull(contArg);
        Validate.notNull(methodStateVar);
        Validate.isTrue(contArg.getType().equals(Type.getType(Continuation.class)));
        Validate.isTrue(methodStateVar.getType().equals(Type.getType(MethodState.class)));

        this.contArg = contArg;
        this.methodStateVar = methodStateVar;
        this.savedLocalsVar = varTable.acquireExtra(Object[].class);
        this.savedStackVar = varTable.acquireExtra(Object[].class);
        this.savedArgumentsVar = varTable.acquireExtra(Object[].class);
        this.savedPartialStackVar = varTable.acquireExtra(Object[].class);
        this.returnValObjectVar = varTable.acquireExtra(Object.class);
    }

    public Variable getContArg() {
        return contArg;
    }

    public Variable getMethodStateVar() {
        return methodStateVar;
    }

    public Variable getSavedLocalsVar() {
        return savedLocalsVar;
    }

    public Variable getSavedStackVar() {
        return savedStackVar;
    }

    public Variable getSavedPartialStackVar() {
        return savedPartialStackVar;
    }

    public Variable getSavedArgumentsVar() {
        return savedArgumentsVar;
    }

    public Variable getTempObjVar2() {
        return returnValObjectVar;
    }

}
