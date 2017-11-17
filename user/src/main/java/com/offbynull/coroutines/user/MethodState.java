/*
 * Copyright (c) 2017, Kasra Faghihi, All rights reserved.
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
package com.offbynull.coroutines.user;

import java.io.Serializable;

/**
 * Do not use -- for internal use only.
 * <p>
 * Holds on to the state of a method frame.
 * @author Kasra Faghihi
 */
public final class MethodState implements Serializable {
    private static final long serialVersionUID = 5L;
    
    private final int methodId;
    private final int methodVersion;
    private final int continuationPoint;
    private final Object[] data;
    private final LockState lockState;

    private MethodState next;
    private MethodState previous;

    /**
     * Do not use -- for internal use only.
     * <p>
     * Constructs a {@link MethodState} object.
     * @param methodId identifier for method at which state was saved
     * @param methodVersion hash for method that identifies version of method for which state was saved
     * @param continuationPoint point in the method at which state was saved (does not refer to offset, just an id that's generated by the
     * instrumenter to mark that point)
     * @param data locals and operand stack at the point which state was saved
     * @param lockState monitors entered at the point which state was saved (may be {@code null})
     */
    public MethodState(int methodId, int methodVersion, int continuationPoint, Object[] data, LockState lockState) {
        if (continuationPoint < 0) {
            throw new IllegalArgumentException();
        }
        if (data == null) {
            throw new NullPointerException();
        }
        this.methodId = methodId;
        this.methodVersion = methodVersion;
        this.continuationPoint = continuationPoint;
        this.data = data;
        this.lockState = lockState;
    }

    /**
     * Do not use -- for internal use only.
     * <p>
     * Get identifier of method for which state was saved
     * @return ID of method for which state was saved
     */
    public int getMethodId() {
        return methodId;
    }

    /**
     * Do not use -- for internal use only.
     * <p>
     * Get version of method for which state was saved
     * @return ID version of method for which state was saved
     */
    public int getMethodVersion() {
        return methodVersion;
    }

    /**
     * Do not use -- for internal use only.
     * <p>
     * Get the point in the code at which state was saved (does not refer to offset, just an id that's generated by the
     * instrumenter to mark that point)
     * @return point in the code at which state was saved
     */
    public int getContinuationPoint() {
        return continuationPoint;
    }

    /**
     * Do not use -- for internal use only.
     * <p>
     * Get locals and operand stack at the point which state was saved.
     * @return locals and operand stack at the point which state was saved
     */
    public Object[] getData() {
        return data;
    }

    /**
     * Do not use -- for internal use only.
     * <p>
     * Get the monitors entered at the point which state was saved.
     * @return monitors entered at the point which state was saved
     */
    public LockState getLockState() {
        return lockState;
    }



    
    
    
    
    
    
    
    /**
     * Do not use -- for internal use only.
     * <p>
     * Get the next method state.
     * @return next method state
     */
    MethodState getNext() {
        return next;
    }

    /**
     * Do not use -- for internal use only.
     * <p>
     * Set the next method state.
     * @param next next method state
     */
    void setNext(MethodState next) {
        this.next = next;
        if (next != null) {
            next.previous = this;
        }
    }

    /**
     * Do not use -- for internal use only.
     * <p>
     * Get the previous method state.
     * @return previous method state
     */
    MethodState getPrevious() {
        return previous;
    }

    /**
     * Do not use -- for internal use only.
     * <p>
     * Set the previous method state.
     * @param previous previous method state
     */
    void setPrevious(MethodState previous) {
        this.previous = previous;
        if (previous != null) {
            previous.next = this;
        }
    }
    
}
