package com.mbakshi.decodeframe.FrameResources.Util.Allocate;

import com.mbakshi.decodeframe.FrameResources.Assertions;
import com.mbakshi.decodeframe.FrameResources.Util.Utilities;

import java.util.Arrays;

/**
 * Created by mbakshi on 20/08/15.
 */
public final class CustomAllocator implements Allocator{
    private static final int INITIAL_RECYCLED_ALLOCATION_CAPACITY = 100;

    private final int individualAllocationSize;

    private int allocatedCount;
    private int recycledCount;
    private Allocation[] recycledAllocations;

    /**
     * Constructs an empty pool.
     *
     * @param individualAllocationSize The length of each individual allocation.
     */
    public CustomAllocator(int individualAllocationSize) {
        Assertions.checkArgument(individualAllocationSize > 0);
        this.individualAllocationSize = individualAllocationSize;
        this.recycledAllocations = new Allocation[INITIAL_RECYCLED_ALLOCATION_CAPACITY];
    }

    @Override
    public synchronized Allocation allocate() {
        allocatedCount++;
        return recycledCount > 0 ? recycledAllocations[--recycledCount]
                : new Allocation(new byte[individualAllocationSize], 0);
    }

    @Override
    public synchronized void release(Allocation allocation) {
        // Weak sanity check that the allocation probably originated from this pool.
        Assertions.checkArgument(allocation.data.length == individualAllocationSize);
        allocatedCount--;
        if (recycledCount == recycledAllocations.length) {
            recycledAllocations = Arrays.copyOf(recycledAllocations, recycledAllocations.length * 2);
        }
        recycledAllocations[recycledCount++] = allocation;
        // Wake up threads waiting for the allocated size to drop.
        notifyAll();
    }

    @Override
    public synchronized void trim(int targetSize) {
        int targetAllocationCount = Utilities.ceilDivide(targetSize, individualAllocationSize);
        int targetRecycledAllocationCount = Math.max(0, targetAllocationCount - allocatedCount);
        if (targetRecycledAllocationCount < recycledCount) {
            Arrays.fill(recycledAllocations, targetRecycledAllocationCount, recycledCount, null);
            recycledCount = targetRecycledAllocationCount;
        }
    }

    @Override
    public synchronized int getTotalBytesAllocated() {
        return allocatedCount * individualAllocationSize;
    }

    @Override
    public int getIndividualAllocationLength() {
        return individualAllocationSize;
    }

    /**
     * Blocks execution until the allocated number of bytes allocated is not greater than the
     * threshold, or the thread is interrupted.
     */
    @Override
    public synchronized void blockWhileTotalBytesAllocatedExceeds(int limit)
            throws InterruptedException {
        while (getTotalBytesAllocated() > limit) {
            wait();
        }
    }

    @Override
    public boolean totalBytesAllocatedExceeds(int limit) {
        if(getTotalBytesAllocated() > limit) {
            return true;
        }
        return false;
    }
}
