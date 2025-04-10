package com.google.protobuf;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A {@link CodedOutputStream} that writes directly to a direct {@link ByteBuffer}, using only
 * safe operations..
 */
final class SafeDirectNioEncoder extends CodedOutputStream {
    private final ByteBuffer originalBuffer;
    private final ByteBuffer buffer;
    private final int initialPosition;

    SafeDirectNioEncoder(ByteBuffer buffer) {
        this.originalBuffer = buffer;
        this.buffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        initialPosition = buffer.position();
    }

    @Override
    public void writeTag(final int fieldNumber, final int wireType) throws IOException {
        writeUInt32NoTag(WireFormat.makeTag(fieldNumber, wireType));
    }

    @Override
    public void writeInt32(final int fieldNumber, final int value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        writeInt32NoTag(value);
    }

    @Override
    public void writeUInt32(final int fieldNumber, final int value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        writeUInt32NoTag(value);
    }

    @Override
    public void writeFixed32(final int fieldNumber, final int value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED32);
        writeFixed32NoTag(value);
    }

    @Override
    public void writeUInt64(final int fieldNumber, final long value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        writeUInt64NoTag(value);
    }

    @Override
    public void writeFixed64(final int fieldNumber, final long value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED64);
        writeFixed64NoTag(value);
    }

    @Override
    public void writeBool(final int fieldNumber, final boolean value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        write((byte) (value ? 1 : 0));
    }

    @Override
    public void writeString(final int fieldNumber, final String value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeStringNoTag(value);
    }

    @Override
    public void writeBytes(final int fieldNumber, final ByteString value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeBytesNoTag(value);
    }

    @Override
    public void writeByteArray(final int fieldNumber, final byte[] value) throws IOException {
        writeByteArray(fieldNumber, value, 0, value.length);
    }

    @Override
    public void writeByteArray(
            final int fieldNumber, final byte[] value, final int offset, final int length)
            throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeByteArrayNoTag(value, offset, length);
    }

    @Override
    public void writeByteBuffer(final int fieldNumber, final ByteBuffer value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeUInt32NoTag(value.capacity());
        writeRawBytes(value);
    }

    @Override
    public void writeMessage(final int fieldNumber, final MessageLite value) throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeMessageNoTag(value);
    }

    @Override
    void writeMessage(final int fieldNumber, final MessageLite value, Schema schema)
            throws IOException {
        writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        writeMessageNoTag(value, schema);
    }

    @Override
    public void writeMessageSetExtension(final int fieldNumber, final MessageLite value)
            throws IOException {
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_START_GROUP);
        writeUInt32(WireFormat.MESSAGE_SET_TYPE_ID, fieldNumber);
        writeMessage(WireFormat.MESSAGE_SET_MESSAGE, value);
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_END_GROUP);
    }

    @Override
    public void writeRawMessageSetExtension(final int fieldNumber, final ByteString value)
            throws IOException {
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_START_GROUP);
        writeUInt32(WireFormat.MESSAGE_SET_TYPE_ID, fieldNumber);
        writeBytes(WireFormat.MESSAGE_SET_MESSAGE, value);
        writeTag(WireFormat.MESSAGE_SET_ITEM, WireFormat.WIRETYPE_END_GROUP);
    }

    @Override
    public void writeMessageNoTag(final MessageLite value) throws IOException {
        writeUInt32NoTag(value.getSerializedSize());
        value.writeTo(this);
    }

    @Override
    void writeMessageNoTag(final MessageLite value, Schema schema) throws IOException {
        writeUInt32NoTag(((AbstractMessageLite) value).getSerializedSize(schema));
        schema.writeTo(value, wrapper);
    }

    @Override
    public void write(byte value) throws IOException {
        try {
            buffer.put(value);
        } catch (BufferOverflowException e) {
            throw new OutOfSpaceException(e);
        }
    }

    @Override
    public void writeBytesNoTag(final ByteString value) throws IOException {
        writeUInt32NoTag(value.size());
        value.writeTo(this);
    }

    @Override
    public void writeByteArrayNoTag(final byte[] value, int offset, int length) throws IOException {
        writeUInt32NoTag(length);
        write(value, offset, length);
    }

    @Override
    public void writeRawBytes(final ByteBuffer value) throws IOException {
        if (value.hasArray()) {
            write(value.array(), value.arrayOffset(), value.capacity());
        } else {
            ByteBuffer duplicated = value.duplicate();
            Java8Compatibility.clear(duplicated);
            write(duplicated);
        }
    }

    @Override
    public void writeInt32NoTag(int value) throws IOException {
        if (value >= 0) {
            writeUInt32NoTag(value);
        } else {
            // Must sign-extend.
            writeUInt64NoTag(value);
        }
    }

    @Override
    public void writeUInt32NoTag(int value) throws IOException {
        try {
            while (true) {
                if ((value & ~0x7F) == 0) {
                    buffer.put((byte) value);
                    return;
                } else {
                    buffer.put((byte) ((value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
        } catch (BufferOverflowException e) {
            throw new OutOfSpaceException(e);
        }
    }

    @Override
    public void writeFixed32NoTag(int value) throws IOException {
        try {
            buffer.putInt(value);
        } catch (BufferOverflowException e) {
            throw new OutOfSpaceException(e);
        }
    }

    @Override
    public void writeUInt64NoTag(long value) throws IOException {
        try {
            while (true) {
                if ((value & ~0x7FL) == 0) {
                    buffer.put((byte) value);
                    return;
                } else {
                    buffer.put((byte) (((int) value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
        } catch (BufferOverflowException e) {
            throw new OutOfSpaceException(e);
        }
    }

    @Override
    public void writeFixed64NoTag(long value) throws IOException {
        try {
            buffer.putLong(value);
        } catch (BufferOverflowException e) {
            throw new OutOfSpaceException(e);
        }
    }

    @Override
    public void write(byte[] value, int offset, int length) throws IOException {
        try {
            buffer.put(value, offset, length);
        } catch (IndexOutOfBoundsException e) {
            throw new OutOfSpaceException(e);
        } catch (BufferOverflowException e) {
            throw new OutOfSpaceException(e);
        }
    }

    @Override
    public void writeLazy(byte[] value, int offset, int length) throws IOException {
        write(value, offset, length);
    }

    @Override
    public void write(ByteBuffer value) throws IOException {
        try {
            buffer.put(value);
        } catch (BufferOverflowException e) {
            throw new OutOfSpaceException(e);
        }
    }

    @Override
    public void writeLazy(ByteBuffer value) throws IOException {
        write(value);
    }

    @Override
    public void writeStringNoTag(String value) throws IOException {
        final int startPos = buffer.position();
        try {
            // UTF-8 byte length of the string is at least its UTF-16 code unit length (value.length()),
            // and at most 3 times of it. We take advantage of this in both branches below.
            final int maxEncodedSize = value.length() * Utf8.MAX_BYTES_PER_CHAR;
            final int maxLengthVarIntSize = computeUInt32SizeNoTag(maxEncodedSize);
            final int minLengthVarIntSize = computeUInt32SizeNoTag(value.length());
            if (minLengthVarIntSize == maxLengthVarIntSize) {
                // Save the current position and increment past the length field. We'll come back
                // and write the length field after the encoding is complete.
                final int startOfBytes = buffer.position() + minLengthVarIntSize;
                Java8Compatibility.position(buffer, startOfBytes);

                // Encode the string.
                encode(value);

                // Now go back to the beginning and write the length.
                int endOfBytes = buffer.position();
                Java8Compatibility.position(buffer, startPos);
                writeUInt32NoTag(endOfBytes - startOfBytes);

                // Reposition the buffer past the written data.
                Java8Compatibility.position(buffer, endOfBytes);
            } else {
                final int length = Utf8.encodedLength(value);
                writeUInt32NoTag(length);
                encode(value);
            }
        } catch (Utf8.UnpairedSurrogateException e) {
            // Roll back the change and convert to an IOException.
            Java8Compatibility.position(buffer, startPos);

            // TODO: We should throw an IOException here instead.
            inefficientWriteStringNoTag(value, e);
        } catch (IllegalArgumentException e) {
            // Thrown by buffer.position() if out of range.
            throw new OutOfSpaceException(e);
        }
    }

    @Override
    public void flush() {
        // Update the position of the original buffer.
        Java8Compatibility.position(originalBuffer, buffer.position());
    }

    @Override
    public int spaceLeft() {
        return buffer.remaining();
    }

    @Override
    public int getTotalBytesWritten() {
        return buffer.position() - initialPosition;
    }

    private void encode(String value) throws IOException {
        try {
            Utf8.encodeUtf8(value, buffer);
        } catch (IndexOutOfBoundsException e) {
            throw new OutOfSpaceException(e);
        }
    }
}