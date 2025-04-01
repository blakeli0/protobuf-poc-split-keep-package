package com.google.protobuf;

import static com.google.protobuf.WireFormat.*;
import static java.lang.Math.max;

/** Abstract base class for buffered encoders. */
abstract class AbstractBufferedEncoder extends CodedOutputStream {
    final byte[] buffer;
    final int limit;
    int position;
    int totalBytesWritten;

    AbstractBufferedEncoder(int bufferSize) {
        if (bufferSize < 0) {
            throw new IllegalArgumentException("bufferSize must be >= 0");
        }
        // As an optimization, we require that the buffer be able to store at least 2
        // varints so that we can buffer any integer write (tag + value). This reduces the
        // number of range checks for a single write to 1 (i.e. if there is not enough space
        // to buffer the tag+value, flush and then buffer it).
        this.buffer = new byte[max(bufferSize, MAX_VARINT_SIZE * 2)];
        this.limit = buffer.length;
    }

    @Override
    public final int spaceLeft() {
        throw new UnsupportedOperationException(
                "spaceLeft() can only be called on CodedOutputStreams that are "
                        + "writing to a flat array or ByteBuffer.");
    }

    @Override
    public final int getTotalBytesWritten() {
        return totalBytesWritten;
    }

    /**
     * This method does not perform bounds checking on the array. Checking array bounds is the
     * responsibility of the caller.
     */
    final void buffer(byte value) {
        buffer[position++] = value;
        totalBytesWritten++;
    }

    /**
     * This method does not perform bounds checking on the array. Checking array bounds is the
     * responsibility of the caller.
     */
    final void bufferTag(final int fieldNumber, final int wireType) {
        bufferUInt32NoTag(WireFormat.makeTag(fieldNumber, wireType));
    }

    /**
     * This method does not perform bounds checking on the array. Checking array bounds is the
     * responsibility of the caller.
     */
    final void bufferInt32NoTag(final int value) {
        if (value >= 0) {
            bufferUInt32NoTag(value);
        } else {
            // Must sign-extend.
            bufferUInt64NoTag(value);
        }
    }

    /**
     * This method does not perform bounds checking on the array. Checking array bounds is the
     * responsibility of the caller.
     */
    final void bufferUInt32NoTag(int value) {
        if (HAS_UNSAFE_ARRAY_OPERATIONS) {
            final long originalPos = position;
            while (true) {
                if ((value & ~0x7F) == 0) {
                    UnsafeUtil.putByte(buffer, position++, (byte) value);
                    break;
                } else {
                    UnsafeUtil.putByte(buffer, position++, (byte) ((value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
            int delta = (int) (position - originalPos);
            totalBytesWritten += delta;
        } else {
            while (true) {
                if ((value & ~0x7F) == 0) {
                    buffer[position++] = (byte) value;
                    totalBytesWritten++;
                    return;
                } else {
                    buffer[position++] = (byte) ((value & 0x7F) | 0x80);
                    totalBytesWritten++;
                    value >>>= 7;
                }
            }
        }
    }

    /**
     * This method does not perform bounds checking on the array. Checking array bounds is the
     * responsibility of the caller.
     */
    final void bufferUInt64NoTag(long value) {
        if (HAS_UNSAFE_ARRAY_OPERATIONS) {
            final long originalPos = position;
            while (true) {
                if ((value & ~0x7FL) == 0) {
                    UnsafeUtil.putByte(buffer, position++, (byte) value);
                    break;
                } else {
                    UnsafeUtil.putByte(buffer, position++, (byte) (((int) value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
            int delta = (int) (position - originalPos);
            totalBytesWritten += delta;
        } else {
            while (true) {
                if ((value & ~0x7FL) == 0) {
                    buffer[position++] = (byte) value;
                    totalBytesWritten++;
                    return;
                } else {
                    buffer[position++] = (byte) (((int) value & 0x7F) | 0x80);
                    totalBytesWritten++;
                    value >>>= 7;
                }
            }
        }
    }

    /**
     * This method does not perform bounds checking on the array. Checking array bounds is the
     * responsibility of the caller.
     */
    final void bufferFixed32NoTag(int value) {
        buffer[position++] = (byte) (value & 0xFF);
        buffer[position++] = (byte) ((value >> 8) & 0xFF);
        buffer[position++] = (byte) ((value >> 16) & 0xFF);
        buffer[position++] = (byte) ((value >> 24) & 0xFF);
        totalBytesWritten += FIXED32_SIZE;
    }

    /**
     * This method does not perform bounds checking on the array. Checking array bounds is the
     * responsibility of the caller.
     */
    final void bufferFixed64NoTag(long value) {
        buffer[position++] = (byte) (value & 0xFF);
        buffer[position++] = (byte) ((value >> 8) & 0xFF);
        buffer[position++] = (byte) ((value >> 16) & 0xFF);
        buffer[position++] = (byte) ((value >> 24) & 0xFF);
        buffer[position++] = (byte) ((int) (value >> 32) & 0xFF);
        buffer[position++] = (byte) ((int) (value >> 40) & 0xFF);
        buffer[position++] = (byte) ((int) (value >> 48) & 0xFF);
        buffer[position++] = (byte) ((int) (value >> 56) & 0xFF);
        totalBytesWritten += FIXED64_SIZE;
    }
}