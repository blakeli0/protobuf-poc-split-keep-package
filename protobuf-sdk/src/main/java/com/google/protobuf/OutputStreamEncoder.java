package com.google.protobuf;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import static com.google.protobuf.WireFormat.*;
import static com.google.protobuf.WireFormat.FIXED64_SIZE;

/**
 * An {@link CodedOutputStream} that decorates an {@link OutputStream}. It performs internal
 * buffering to optimize writes to the {@link OutputStream}.
 */
final class OutputStreamEncoder extends AbstractBufferedEncoder {
    private final OutputStream out;

    OutputStreamEncoder(OutputStream out, int bufferSize) {
        super(bufferSize);
        if (out == null) {
            throw new NullPointerException("out");
        }
        this.out = out;
    }

    @Override
    public void writeTag(final int fieldNumber, final int wireType) throws IOException {
        writeUInt32NoTag(WireFormat.makeTag(fieldNumber, wireType));
    }

    @Override
    public void writeInt32(final int fieldNumber, final int value) throws IOException {
        flushIfNotAvailable(MAX_VARINT_SIZE * 2);
        bufferTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        bufferInt32NoTag(value);
    }

    @Override
    public void writeUInt32(final int fieldNumber, final int value) throws IOException {
        flushIfNotAvailable(MAX_VARINT_SIZE * 2);
        bufferTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        bufferUInt32NoTag(value);
    }

    @Override
    public void writeFixed32(final int fieldNumber, final int value) throws IOException {
        flushIfNotAvailable(MAX_VARINT_SIZE + FIXED32_SIZE);
        bufferTag(fieldNumber, WireFormat.WIRETYPE_FIXED32);
        bufferFixed32NoTag(value);
    }

    @Override
    public void writeUInt64(final int fieldNumber, final long value) throws IOException {
        flushIfNotAvailable(MAX_VARINT_SIZE * 2);
        bufferTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        bufferUInt64NoTag(value);
    }

    @Override
    public void writeFixed64(final int fieldNumber, final long value) throws IOException {
        flushIfNotAvailable(MAX_VARINT_SIZE + FIXED64_SIZE);
        bufferTag(fieldNumber, WireFormat.WIRETYPE_FIXED64);
        bufferFixed64NoTag(value);
    }

    @Override
    public void writeBool(final int fieldNumber, final boolean value) throws IOException {
        flushIfNotAvailable(MAX_VARINT_SIZE + 1);
        bufferTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
        buffer((byte) (value ? 1 : 0));
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
        if (position == limit) {
            doFlush();
        }

        buffer(value);
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
        flushIfNotAvailable(MAX_VARINT32_SIZE);
        bufferUInt32NoTag(value);
    }

    @Override
    public void writeFixed32NoTag(final int value) throws IOException {
        flushIfNotAvailable(FIXED32_SIZE);
        bufferFixed32NoTag(value);
    }

    @Override
    public void writeUInt64NoTag(long value) throws IOException {
        flushIfNotAvailable(MAX_VARINT_SIZE);
        bufferUInt64NoTag(value);
    }

    @Override
    public void writeFixed64NoTag(final long value) throws IOException {
        flushIfNotAvailable(FIXED64_SIZE);
        bufferFixed64NoTag(value);
    }

    @Override
    public void writeStringNoTag(String value) throws IOException {
        try {
            // UTF-8 byte length of the string is at least its UTF-16 code unit length (value.length()),
            // and at most 3 times of it. We take advantage of this in both branches below.
            final int maxLength = value.length() * Utf8.MAX_BYTES_PER_CHAR;
            final int maxLengthVarIntSize = computeUInt32SizeNoTag(maxLength);

            // If we are streaming and the potential length is too big to fit in our buffer, we take the
            // slower path.
            if (maxLengthVarIntSize + maxLength > limit) {
                // Allocate a byte[] that we know can fit the string and encode into it. String.getBytes()
                // does the same internally and then does *another copy* to return a byte[] of exactly the
                // right size. We can skip that copy and just writeRawBytes up to the actualLength of the
                // UTF-8 encoded bytes.
                final byte[] encodedBytes = new byte[maxLength];
                int actualLength = Utf8.encode(value, encodedBytes, 0, maxLength);
                writeUInt32NoTag(actualLength);
                writeLazy(encodedBytes, 0, actualLength);
                return;
            }

            // Fast path: we have enough space available in our buffer for the string...
            if (maxLengthVarIntSize + maxLength > limit - position) {
                // Flush to free up space.
                doFlush();
            }

            // Optimize for the case where we know this length results in a constant varint length as
            // this saves a pass for measuring the length of the string.
            final int minLengthVarIntSize = computeUInt32SizeNoTag(value.length());
            int oldPosition = position;
            final int length;
            try {
                if (minLengthVarIntSize == maxLengthVarIntSize) {
                    position = oldPosition + minLengthVarIntSize;
                    int newPosition = Utf8.encode(value, buffer, position, limit - position);
                    // Since this class is stateful and tracks the position, we rewind and store the
                    // state, prepend the length, then reset it back to the end of the string.
                    position = oldPosition;
                    length = newPosition - oldPosition - minLengthVarIntSize;
                    bufferUInt32NoTag(length);
                    position = newPosition;
                } else {
                    length = Utf8.encodedLength(value);
                    bufferUInt32NoTag(length);
                    position = Utf8.encode(value, buffer, position, length);
                }
                totalBytesWritten += length;
            } catch (Utf8.UnpairedSurrogateException e) {
                // Be extra careful and restore the original position for retrying the write with the
                // less efficient path.
                totalBytesWritten -= position - oldPosition;
                position = oldPosition;
                throw e;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new OutOfSpaceException(e);
            }
        } catch (Utf8.UnpairedSurrogateException e) {
            inefficientWriteStringNoTag(value, e);
        }
    }

    @Override
    public void flush() throws IOException {
        if (position > 0) {
            // Flush the buffer.
            doFlush();
        }
    }

    @Override
    public void write(byte[] value, int offset, int length) throws IOException {
        if (limit - position >= length) {
            // We have room in the current buffer.
            System.arraycopy(value, offset, buffer, position, length);
            position += length;
            totalBytesWritten += length;
        } else {
            // Write extends past current buffer.  Fill the rest of this buffer and
            // flush.
            final int bytesWritten = limit - position;
            System.arraycopy(value, offset, buffer, position, bytesWritten);
            offset += bytesWritten;
            length -= bytesWritten;
            position = limit;
            totalBytesWritten += bytesWritten;
            doFlush();

            // Now deal with the rest.
            // Since we have an output stream, this is our buffer
            // and buffer offset == 0
            if (length <= limit) {
                // Fits in new buffer.
                System.arraycopy(value, offset, buffer, 0, length);
                position = length;
            } else {
                // Write is very big.  Let's do it all at once.
                out.write(value, offset, length);
            }
            totalBytesWritten += length;
        }
    }

    @Override
    public void writeLazy(byte[] value, int offset, int length) throws IOException {
        write(value, offset, length);
    }

    @Override
    public void write(ByteBuffer value) throws IOException {
        int length = value.remaining();
        if (limit - position >= length) {
            // We have room in the current buffer.
            value.get(buffer, position, length);
            position += length;
            totalBytesWritten += length;
        } else {
            // Write extends past current buffer.  Fill the rest of this buffer and
            // flush.
            final int bytesWritten = limit - position;
            value.get(buffer, position, bytesWritten);
            length -= bytesWritten;
            position = limit;
            totalBytesWritten += bytesWritten;
            doFlush();

            // Now deal with the rest.
            // Since we have an output stream, this is our buffer
            // and buffer offset == 0
            while (length > limit) {
                // Copy data into the buffer before writing it to OutputStream.
                value.get(buffer, 0, limit);
                out.write(buffer, 0, limit);
                length -= limit;
                totalBytesWritten += limit;
            }
            value.get(buffer, 0, length);
            position = length;
            totalBytesWritten += length;
        }
    }

    @Override
    public void writeLazy(ByteBuffer value) throws IOException {
        write(value);
    }

    private void flushIfNotAvailable(int requiredSize) throws IOException {
        if (limit - position < requiredSize) {
            doFlush();
        }
    }

    private void doFlush() throws IOException {
        out.write(buffer, 0, position);
        position = 0;
    }
}