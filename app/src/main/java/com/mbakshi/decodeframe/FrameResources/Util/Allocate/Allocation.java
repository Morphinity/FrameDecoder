package com.mbakshi.decodeframe.FrameResources.Util.Allocate;

/**
 * Created by mbakshi on 20/08/15.
 */
public final class Allocation {
    /**
     * The array containing the allocated space. The allocated space may not be at the start of the
     * array, and so {@link #translateOffset(int)} method must be used when indexing into it.
     */
    public final byte[] data;

    private final int offset;

    /**
     * @param data The array containing the allocated space.
     * @param offset The offset of the allocated space within the array.
     */
    public Allocation(byte[] data, int offset) {
        this.data = data;
        this.offset = offset;
    }

    /**
     * Translates a zero-based offset into the allocation to the corresponding {@link #data} offset.
     *
     * @param offset The zero-based offset to translate.
     * @return The corresponding offset in {@link #data}.
     */
    public int translateOffset(int offset) {
        return this.offset + offset;
    }
}
