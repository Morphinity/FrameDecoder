package com.mbakshi.decodeframe.FrameResources.Util.Allocate;

/**
 * Created by mbakshi on 20/08/15.
 */
public interface Allocator {

    Allocation allocate();

    void release(Allocation allocation);

    void trim(int targetSize);

    int getTotalBytesAllocated();

    int getIndividualAllocationLength();

    void blockWhileTotalBytesAllocatedExceeds(int limit) throws InterruptedException;

    boolean totalBytesAllocatedExceeds(int limit);
}
