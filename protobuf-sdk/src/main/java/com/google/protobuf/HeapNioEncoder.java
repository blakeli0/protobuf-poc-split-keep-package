package com.google.protobuf;

import java.nio.ByteBuffer;

/**
 * A {@link CodedOutputStream} that writes directly to a heap {@link ByteBuffer}. Writes are done
 * directly to the underlying array. The buffer position is only updated after a flush.
 */
final class HeapNioEncoder extends ArrayEncoder {
    private final ByteBuffer byteBuffer;
    private int initialPosition;

    HeapNioEncoder(ByteBuffer byteBuffer) {
        super(
                byteBuffer.array(),
                byteBuffer.arrayOffset() + byteBuffer.position(),
                byteBuffer.remaining());
        this.byteBuffer = byteBuffer;
        this.initialPosition = byteBuffer.position();
    }

    @Override
    public void flush() {
        // Update the position on the buffer.
        Java8Compatibility.position(byteBuffer, initialPosition + getTotalBytesWritten());
    }
}