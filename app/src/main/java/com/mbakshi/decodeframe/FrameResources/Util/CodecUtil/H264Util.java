package com.mbakshi.decodeframe.FrameResources.Util.CodecUtil;

import com.mbakshi.decodeframe.FrameResources.Assertions;
import com.mbakshi.decodeframe.FrameResources.Util.ParsableByteArray;

import java.nio.ByteBuffer;

/**
 * Created by mbakshi on 20/08/15.
 */
public final class H264Util {
    /** Four initial bytes that must prefix H.264/AVC NAL units for decoding. */
    public static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};

    /**
     * Replaces length prefixes of NAL units in {@code buffer} with start code prefixes, within the
     * {@code size} bytes preceding the buffer's position.
     */
    public static void replaceLengthPrefixesWithAvcStartCodes(ByteBuffer buffer, int size) {
        int sampleOffset = buffer.position() - size;
        int position = sampleOffset;
        while (position < sampleOffset + size) {
            buffer.position(position);
            int length = readUnsignedIntToInt(buffer);
            buffer.position(position);
            buffer.put(NAL_START_CODE);
            position += length + 4;
        }
        buffer.position(sampleOffset + size);
    }

    /**
     * Constructs and returns a NAL unit with a start code followed by the data in {@code atom}.
     */
    public static byte[] parseChildNalUnit(ParsableByteArray atom) {
        int length = atom.readUnsignedShort();
        int offset = atom.getPosition();
        atom.skipBytes(length);
        return CodecSpecificDataUtil.buildNalUnit(atom.data, offset, length);
    }

    /**
     * Gets the type of the NAL unit in {@code data} that starts at {@code offset}.
     *
     * @param data The data to search.
     * @param offset The start offset of a NAL unit. Must lie between {@code -3} (inclusive) and
     *     {@code data.length - 3} (exclusive).
     * @return The type of the unit.
     */
    public static int getNalUnitType(byte[] data, int offset) {
        return data[offset + 3] & 0x1F;
    }

    /**
     * Finds the first NAL unit in {@code data}.
     * <p>
     * If {@code prefixFlags} is null then the first four bytes of a NAL unit must be entirely
     * contained within the part of the array being searched in order for it to be found.
     * <p>
     * When {@code prefixFlags} is non-null, this method supports finding NAL units whose first four
     * bytes span {@code data} arrays passed to successive calls. To use this feature, pass the same
     * {@code prefixFlags} parameter to successive calls. State maintained in this parameter enables
     * the detection of such NAL units. Note that when using this feature, the return value may be 3,
     * 2 or 1 less than {@code startOffset}, to indicate a NAL unit starting 3, 2 or 1 bytes before
     * the first byte in the current array.
     *
     * @param data The data to search.
     * @param startOffset The offset (inclusive) in the data to start the search.
     * @param endOffset The offset (exclusive) in the data to end the search.
     * @param prefixFlags A boolean array whose first three elements are used to store the state
     *     required to detect NAL units where the NAL unit prefix spans array boundaries. The array
     *     must be at least 3 elements long.
     * @return The offset of the NAL unit, or {@code endOffset} if a NAL unit was not found.
     */
    public static int findNalUnit(byte[] data, int startOffset, int endOffset,
                                  boolean[] prefixFlags) {
        int length = endOffset - startOffset;

        Assertions.checkState(length >= 0);
        if (length == 0) {
            return endOffset;
        }

        if (prefixFlags != null) {
            if (prefixFlags[0]) {
                clearPrefixFlags(prefixFlags);
                return startOffset - 3;
            } else if (length > 1 && prefixFlags[1] && data[startOffset] == 1) {
                clearPrefixFlags(prefixFlags);
                return startOffset - 2;
            } else if (length > 2 && prefixFlags[2] && data[startOffset] == 0
                    && data[startOffset + 1] == 1) {
                clearPrefixFlags(prefixFlags);
                return startOffset - 1;
            }
        }

        int limit = endOffset - 1;
        // We're looking for the NAL unit start code prefix 0x000001, followed by a byte that matches
        // the specified type. The value of i tracks the index of the third byte in the four bytes
        // being examined.
        for (int i = startOffset + 2; i < limit; i += 3) {
            if ((data[i] & 0xFE) != 0) {
                // There isn't a NAL prefix here, or at the next two positions. Do nothing and let the
                // loop advance the index by three.
            } else if (data[i - 2] == 0 && data[i - 1] == 0 && data[i] == 1) {
                if (prefixFlags != null) {
                    clearPrefixFlags(prefixFlags);
                }
                return i - 2;
            } else {
                // There isn't a NAL prefix here, but there might be at the next position. We should
                // only skip forward by one. The loop will skip forward by three, so subtract two here.
                i -= 2;
            }
        }

        if (prefixFlags != null) {
            // True if the last three bytes in the data seen so far are {0,0,1}.
            prefixFlags[0] = length > 2
                    ? (data[endOffset - 3] == 0 && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
                    : length == 2 ? (prefixFlags[2] && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
                    : (prefixFlags[1] && data[endOffset - 1] == 1);
            // True if the last three bytes in the data seen so far are {0,0}.
            prefixFlags[1] = length > 1 ? data[endOffset - 2] == 0 && data[endOffset - 1] == 0
                    : prefixFlags[2] && data[endOffset - 1] == 0;
            // True if the last three bytes in the data seen so far are {0}.
            prefixFlags[2] = data[endOffset - 1] == 0;
        }

        return endOffset;
    }

    /**
     * Clears prefix flags, as used by {@link #findNalUnit(byte[], int, int, boolean[])}.
     *
     * @param prefixFlags The flags to clear.
     */
    public static void clearPrefixFlags(boolean[] prefixFlags) {
        prefixFlags[0] = false;
        prefixFlags[1] = false;
        prefixFlags[2] = false;
    }

    /**
     * Reads an unsigned integer into an integer. This method is suitable for use when it can be
     * assumed that the top bit will always be set to zero.
     *
     * @throws IllegalArgumentException If the top bit of the input data is set.
     */
    private static int readUnsignedIntToInt(ByteBuffer data) {
        int result = 0xFF & data.get();
        for (int i = 1; i < 4; i++) {
            result <<= 8;
            result |= 0xFF & data.get();
        }
        if (result < 0) {
            throw new IllegalArgumentException("Top bit not zero: " + result);
        }
        return result;
    }

    private H264Util() {
        // Prevent instantiation.
    }

}
