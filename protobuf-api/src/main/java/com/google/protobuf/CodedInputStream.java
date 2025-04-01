// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

package com.google.protobuf;

import static com.google.protobuf.Internal.EMPTY_BYTE_ARRAY;
import static com.google.protobuf.Internal.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

/**
 * Reads and decodes protocol message fields.
 *
 * <p>This class contains two kinds of methods: methods that read specific protocol message
 * constructs and field types (e.g. {@link #readTag()} and {@link #readInt32()}) and methods that
 * read low-level values (e.g. {@link #readRawVarint32()} and {@link #readRawBytes}). If you are
 * reading encoded protocol messages, you should use the former methods, but if you are reading some
 * other format of your own design, use the latter.
 *
 * @author kenton@google.com Kenton Varda
 */
public abstract class CodedInputStream {
  static final int DEFAULT_BUFFER_SIZE = 4096;
  // Integer.MAX_VALUE == 0x7FFFFFF == INT_MAX from limits.h
  private static final int DEFAULT_SIZE_LIMIT = Integer.MAX_VALUE;
  private static volatile int defaultRecursionLimit = 100;

  private static final Class<?> ARRAY_DECODER = getClassForName("com.google.protobuf.ArrayDecoder");
  private static final Class<?> ITERABLE_DIRECT_BYTE_BUFFER_DECODER = getClassForName("com.google.protobuf.IterableDirectByteBufferDecoder");
  private static final Class<?> STREAM_DECODER = getClassForName("com.google.protobuf.StreamDecoder");
  private static final Class<?> UNSAFE_DIRECT_NIO_DECODER = getClassForName("com.google.protobuf.UnsafeDirectNioDecoder");

  private static <T> Class<T> getClassForName(String name) {
    try {
      return (Class<T>) Class.forName(name);
    } catch (Throwable e) {
      return null;
    }
  }

  /** Visible for subclasses. See setRecursionLimit() */
  int recursionDepth;

  int recursionLimit = defaultRecursionLimit;

  /** Visible for subclasses. See setSizeLimit() */
  int sizeLimit = DEFAULT_SIZE_LIMIT;

  /** Used to adapt to the experimental {@link Reader} interface. */
  CodedInputStreamReader wrapper;

  /** Create a new CodedInputStream wrapping the given InputStream. */
  public static CodedInputStream newInstance(final InputStream input) {
    return newInstance(input, DEFAULT_BUFFER_SIZE);
  }

  /** Create a new CodedInputStream wrapping the given InputStream, with a specified buffer size. */
  public static CodedInputStream newInstance(final InputStream input, int bufferSize) {
    if (bufferSize <= 0) {
      throw new IllegalArgumentException("bufferSize must be > 0");
    }
    if (input == null) {
      // Ideally we would throw here. This is done for backward compatibility.
      return newInstance(EMPTY_BYTE_ARRAY);
    }
    try {
      Constructor<?> constructor = STREAM_DECODER.getDeclaredConstructors()[0];
      return (CodedInputStream) constructor.newInstance(input, bufferSize);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /** Create a new CodedInputStream wrapping the given {@code Iterable <ByteBuffer>}. */
  public static CodedInputStream newInstance(final Iterable<ByteBuffer> input) {
    if (!UnsafeUtil.hasUnsafeByteBufferOperations()) {
      return newInstance(new IterableByteBufferInputStream(input));
    }
    return newInstance(input, false);
  }

  /** Create a new CodedInputStream wrapping the given {@code Iterable <ByteBuffer>}. */
  static CodedInputStream newInstance(
      final Iterable<ByteBuffer> bufs, final boolean bufferIsImmutable) {
    // flag is to check the type of input's ByteBuffers.
    // flag equals 1: all ByteBuffers have array.
    // flag equals 2: all ByteBuffers are direct ByteBuffers.
    // flag equals 3: some ByteBuffers are direct and some have array.
    // flag greater than 3: other cases.
    int flag = 0;
    // Total size of the input
    int totalSize = 0;
    for (ByteBuffer buf : bufs) {
      totalSize += buf.remaining();
      if (buf.hasArray()) {
        flag |= 1;
      } else if (buf.isDirect()) {
        flag |= 2;
      } else {
        flag |= 4;
      }
    }
    if (flag == 2) {
      try {
        Constructor<?> constructor = ITERABLE_DIRECT_BYTE_BUFFER_DECODER.getDeclaredConstructors()[0];
        return (CodedInputStream) constructor.newInstance(bufs, totalSize, bufferIsImmutable);
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    } else {
      // TODO: add another decoders to deal case 1 and 3.
      return newInstance(new IterableByteBufferInputStream(bufs));
    }
  }

  /** Create a new CodedInputStream wrapping the given byte array. */
  public static CodedInputStream newInstance(final byte[] buf) {
    return newInstance(buf, 0, buf.length);
  }

  /** Create a new CodedInputStream wrapping the given byte array slice. */
  public static CodedInputStream newInstance(final byte[] buf, final int off, final int len) {
    return newInstance(buf, off, len, /* bufferIsImmutable= */ false);
  }

  /** Create a new CodedInputStream wrapping the given byte array slice. */
  static CodedInputStream newInstance(
      final byte[] buf, final int off, final int len, final boolean bufferIsImmutable) {
    CodedInputStream arrayDecoder;
    try {
      Constructor<?> constructor = ARRAY_DECODER.getDeclaredConstructors()[0];
      arrayDecoder = (CodedInputStream) constructor.newInstance(buf, off, len, bufferIsImmutable);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
    try {
      // Some uses of CodedInputStream can be more efficient if they know
      // exactly how many bytes are available.  By pushing the end point of the
      // buffer as a limit, we allow them to get this information via
      // getBytesUntilLimit().  Pushing a limit that we know is at the end of
      // the stream can never hurt, since we can never past that point anyway.
      arrayDecoder.pushLimit(len);
    } catch (InvalidProtocolBufferException ex) {
      // The only reason pushLimit() might throw an exception here is if len
      // is negative. Normally pushLimit()'s parameter comes directly off the
      // wire, so it's important to catch exceptions in case of corrupt or
      // malicious data. However, in this case, we expect that len is not a
      // user-supplied value, so we can assume that it being negative indicates
      // a programming error. Therefore, throwing an unchecked exception is
      // appropriate.
      throw new IllegalArgumentException(ex);
    }
    return arrayDecoder;
  }

  /**
   * Create a new CodedInputStream wrapping the given ByteBuffer. The data starting from the
   * ByteBuffer's current position to its limit will be read. The returned CodedInputStream may or
   * may not share the underlying data in the ByteBuffer, therefore the ByteBuffer cannot be changed
   * while the CodedInputStream is in use. Note that the ByteBuffer's position won't be changed by
   * this function. Concurrent calls with the same ByteBuffer object are safe if no other thread is
   * trying to alter the ByteBuffer's status.
   */
  public static CodedInputStream newInstance(ByteBuffer buf) {
    return newInstance(buf, /* bufferIsImmutable= */ false);
  }

  /** Create a new CodedInputStream wrapping the given buffer. */
  static CodedInputStream newInstance(ByteBuffer buf, boolean bufferIsImmutable) {
    if (buf.hasArray()) {
      return newInstance(
          buf.array(), buf.arrayOffset() + buf.position(), buf.remaining(), bufferIsImmutable);
    }

    if (buf.isDirect() && UnsafeUtil.hasUnsafeByteBufferOperations()) {
      try {
        Constructor<?> constructor = UNSAFE_DIRECT_NIO_DECODER.getDeclaredConstructors()[0];
        return (CodedInputStream) constructor.newInstance(buf, bufferIsImmutable);
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }

    // The buffer is non-direct and does not expose the underlying array. Using the ByteBuffer API
    // to access individual bytes is very slow, so just copy the buffer to an array.
    // TODO: Re-evaluate with Java 9
    byte[] buffer = new byte[buf.remaining()];
    buf.duplicate().get(buffer);
    return newInstance(buffer, 0, buffer.length, true);
  }

  public void checkRecursionLimit() throws InvalidProtocolBufferException {
    if (recursionDepth >= recursionLimit) {
      throw InvalidProtocolBufferException.recursionLimitExceeded();
    }
  }
  /** Disable construction/inheritance outside of this class. */
  CodedInputStream() {}

  // -----------------------------------------------------------------

  /**
   * Attempt to read a field tag, returning zero if we have reached EOF. Protocol message parsers
   * use this to read tags, since a protocol message may legally end wherever a tag occurs, and zero
   * is not a valid tag number.
   */
  public abstract int readTag() throws IOException;

  /**
   * Verifies that the last call to readTag() returned the given tag value. This is used to verify
   * that a nested group ended with the correct end tag.
   *
   * @throws InvalidProtocolBufferException {@code value} does not match the last tag.
   */
  public abstract void checkLastTagWas(final int value) throws InvalidProtocolBufferException;

  public abstract int getLastTag();

  /**
   * Reads and discards a single field, given its tag value.
   *
   * @return {@code false} if the tag is an endgroup tag, in which case nothing is skipped.
   *     Otherwise, returns {@code true}.
   */
  public abstract boolean skipField(final int tag) throws IOException;

  /**
   * Reads a single field and writes it to output in wire format, given its tag value.
   *
   * @return {@code false} if the tag is an endgroup tag, in which case nothing is skipped.
   *     Otherwise, returns {@code true}.
   * @deprecated use {@code UnknownFieldSet} or {@code UnknownFieldSetLite} to skip to an output
   *     stream.
   */
  @Deprecated
  public abstract boolean skipField(final int tag, final CodedOutputStream output)
      throws IOException;

  /**
   * Reads and discards an entire message. This will read either until EOF or until an endgroup tag,
   * whichever comes first.
   */
  public void skipMessage() throws IOException {
    while (true) {
      final int tag = readTag();
      if (tag == 0) {
        return;
      }
      checkRecursionLimit();
      ++recursionDepth;
      boolean fieldSkipped = skipField(tag);
      --recursionDepth;
      if (!fieldSkipped) {
        return;
      }
    }
  }

  /**
   * Reads an entire message and writes it to output in wire format. This will read either until EOF
   * or until an endgroup tag, whichever comes first.
   */
  public void skipMessage(CodedOutputStream output) throws IOException {
    while (true) {
      final int tag = readTag();
      if (tag == 0) {
        return;
      }
      checkRecursionLimit();
      ++recursionDepth;
      boolean fieldSkipped = skipField(tag, output);
      --recursionDepth;
      if (!fieldSkipped) {
        return;
      }
    }
  }

  // -----------------------------------------------------------------

  /** Read a {@code double} field value from the stream. */
  public abstract double readDouble() throws IOException;

  /** Read a {@code float} field value from the stream. */
  public abstract float readFloat() throws IOException;

  /** Read a {@code uint64} field value from the stream. */
  public abstract long readUInt64() throws IOException;

  /** Read an {@code int64} field value from the stream. */
  public abstract long readInt64() throws IOException;

  /** Read an {@code int32} field value from the stream. */
  public abstract int readInt32() throws IOException;

  /** Read a {@code fixed64} field value from the stream. */
  public abstract long readFixed64() throws IOException;

  /** Read a {@code fixed32} field value from the stream. */
  public abstract int readFixed32() throws IOException;

  /** Read a {@code bool} field value from the stream. */
  public abstract boolean readBool() throws IOException;

  /**
   * Read a {@code string} field value from the stream. If the stream contains malformed UTF-8,
   * replace the offending bytes with the standard UTF-8 replacement character.
   */
  public abstract String readString() throws IOException;

  /**
   * Read a {@code string} field value from the stream. If the stream contains malformed UTF-8,
   * throw exception {@link InvalidProtocolBufferException}.
   */
  public abstract String readStringRequireUtf8() throws IOException;

  /** Read a {@code group} field value from the stream. */
  public abstract void readGroup(
      final int fieldNumber,
      final MessageLite.Builder builder,
      final ExtensionRegistryLite extensionRegistry)
      throws IOException;

  /** Read a {@code group} field value from the stream. */
  public abstract <T extends MessageLite> T readGroup(
      final int fieldNumber, final Parser<T> parser, final ExtensionRegistryLite extensionRegistry)
      throws IOException;

  /**
   * Reads a {@code group} field value from the stream and merges it into the given {@link
   * UnknownFieldSet}.
   *
   * @deprecated UnknownFieldSet.Builder now implements MessageLite.Builder, so you can just call
   *     {@link #readGroup}.
   */
  @Deprecated
  public abstract void readUnknownGroup(final int fieldNumber, final MessageLite.Builder builder)
      throws IOException;

  /** Read an embedded message field value from the stream. */
  public abstract void readMessage(
      final MessageLite.Builder builder, final ExtensionRegistryLite extensionRegistry)
      throws IOException;

  /** Read an embedded message field value from the stream. */
  public abstract <T extends MessageLite> T readMessage(
      final Parser<T> parser, final ExtensionRegistryLite extensionRegistry) throws IOException;

  /** Read a {@code bytes} field value from the stream. */
  public abstract ByteString readBytes() throws IOException;

  /** Read a {@code bytes} field value from the stream. */
  public abstract byte[] readByteArray() throws IOException;

  /** Read a {@code bytes} field value from the stream. */
  public abstract ByteBuffer readByteBuffer() throws IOException;

  /** Read a {@code uint32} field value from the stream. */
  public abstract int readUInt32() throws IOException;

  /**
   * Read an enum field value from the stream. Caller is responsible for converting the numeric
   * value to an actual enum.
   */
  public abstract int readEnum() throws IOException;

  /** Read an {@code sfixed32} field value from the stream. */
  public abstract int readSFixed32() throws IOException;

  /** Read an {@code sfixed64} field value from the stream. */
  public abstract long readSFixed64() throws IOException;

  /** Read an {@code sint32} field value from the stream. */
  public abstract int readSInt32() throws IOException;

  /** Read an {@code sint64} field value from the stream. */
  public abstract long readSInt64() throws IOException;

  // =================================================================

  /** Read a raw Varint from the stream. If larger than 32 bits, discard the upper bits. */
  public abstract int readRawVarint32() throws IOException;

  /** Read a raw Varint from the stream. */
  public abstract long readRawVarint64() throws IOException;

  /** Variant of readRawVarint64 for when uncomfortably close to the limit. */
  /* Visible for testing */
  abstract long readRawVarint64SlowPath() throws IOException;

  /** Read a 32-bit little-endian integer from the stream. */
  public abstract int readRawLittleEndian32() throws IOException;

  /** Read a 64-bit little-endian integer from the stream. */
  public abstract long readRawLittleEndian64() throws IOException;

  // -----------------------------------------------------------------

  /**
   * Enables {@link ByteString} aliasing of the underlying buffer, trading off on buffer pinning for
   * data copies. Only valid for buffer-backed streams.
   */
  public abstract void enableAliasing(boolean enabled);

  /**
   * Set the maximum message recursion depth. In order to prevent malicious messages from causing
   * stack overflows, {@code CodedInputStream} limits how deeply messages may be nested. The default
   * limit is 100.
   *
   * @return the old limit.
   */
  public final int setRecursionLimit(final int limit) {
    if (limit < 0) {
      throw new IllegalArgumentException("Recursion limit cannot be negative: " + limit);
    }
    final int oldLimit = recursionLimit;
    recursionLimit = limit;
    return oldLimit;
  }

  /**
   * Only valid for {@link InputStream}-backed streams.
   *
   * <p>Set the maximum message size. In order to prevent malicious messages from exhausting memory
   * or causing integer overflows, {@code CodedInputStream} limits how large a message may be. The
   * default limit is {@code Integer.MAX_VALUE}. You should set this limit as small as you can
   * without harming your app's functionality. Note that size limits only apply when reading from an
   * {@code InputStream}, not when constructed around a raw byte array.
   *
   * <p>If you want to read several messages from a single CodedInputStream, you could call {@link
   * #resetSizeCounter()} after each one to avoid hitting the size limit.
   *
   * @return the old limit.
   */
  public final int setSizeLimit(final int limit) {
    if (limit < 0) {
      throw new IllegalArgumentException("Size limit cannot be negative: " + limit);
    }
    final int oldLimit = sizeLimit;
    sizeLimit = limit;
    return oldLimit;
  }

  private boolean shouldDiscardUnknownFields = false;

  /**
   * Sets this {@code CodedInputStream} to discard unknown fields. Only applies to full runtime
   * messages; lite messages will always preserve unknowns.
   *
   * <p>Note calling this function alone will have NO immediate effect on the underlying input data.
   * The unknown fields will be discarded during parsing. This affects both Proto2 and Proto3 full
   * runtime.
   */
  final void discardUnknownFields() {
    shouldDiscardUnknownFields = true;
  }

  /**
   * Reverts the unknown fields preservation behavior for Proto2 and Proto3 full runtime to their
   * default.
   */
  final void unsetDiscardUnknownFields() {
    shouldDiscardUnknownFields = false;
  }

  /**
   * Whether unknown fields in this input stream should be discarded during parsing into full
   * runtime messages.
   */
  final boolean shouldDiscardUnknownFields() {
    return shouldDiscardUnknownFields;
  }

  /**
   * Resets the current size counter to zero (see {@link #setSizeLimit(int)}). Only valid for {@link
   * InputStream}-backed streams.
   */
  public abstract void resetSizeCounter();

  /**
   * Sets {@code currentLimit} to (current position) + {@code byteLimit}. This is called when
   * descending into a length-delimited embedded message.
   *
   * <p>Note that {@code pushLimit()} does NOT affect how many bytes the {@code CodedInputStream}
   * reads from an underlying {@code InputStream} when refreshing its buffer. If you need to prevent
   * reading past a certain point in the underlying {@code InputStream} (e.g. because you expect it
   * to contain more data after the end of the message which you need to handle differently) then
   * you must place a wrapper around your {@code InputStream} which limits the amount of data that
   * can be read from it.
   *
   * @return the old limit.
   */
  public abstract int pushLimit(int byteLimit) throws InvalidProtocolBufferException;

  /**
   * Discards the current limit, returning to the previous limit.
   *
   * @param oldLimit The old limit, as returned by {@code pushLimit}.
   */
  public abstract void popLimit(final int oldLimit);

  /**
   * Returns the number of bytes to be read before the current limit. If no limit is set, returns
   * -1.
   */
  public abstract int getBytesUntilLimit();

  /**
   * Returns true if the stream has reached the end of the input. This is the case if either the end
   * of the underlying input source has been reached or if the stream has reached a limit created
   * using {@link #pushLimit(int)}. This function may get blocked when using StreamDecoder as it
   * invokes {@link StreamDecoder#tryRefillBuffer(int)} in this function which will try to read
   * bytes from input.
   */
  public abstract boolean isAtEnd() throws IOException;

  /**
   * The total bytes read up to the current position. Calling {@link #resetSizeCounter()} resets
   * this value to zero.
   */
  public abstract int getTotalBytesRead();

  /**
   * Read one byte from the input.
   *
   * @throws InvalidProtocolBufferException The end of the stream or the current limit was reached.
   */
  public abstract byte readRawByte() throws IOException;

  /**
   * Read a fixed size of bytes from the input.
   *
   * @throws InvalidProtocolBufferException The end of the stream or the current limit was reached.
   */
  public abstract byte[] readRawBytes(final int size) throws IOException;

  /**
   * Reads and discards {@code size} bytes.
   *
   * @throws InvalidProtocolBufferException The end of the stream or the current limit was reached.
   */
  public abstract void skipRawBytes(final int size) throws IOException;

  /**
   * Decode a ZigZag-encoded 32-bit value. ZigZag encodes signed integers into values that can be
   * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
   * to be varint encoded, thus always taking 10 bytes on the wire.)
   *
   * @param n An unsigned 32-bit integer, stored in a signed int because Java has no explicit
   *     unsigned support.
   * @return A signed 32-bit integer.
   */
  public static int decodeZigZag32(final int n) {
    return (n >>> 1) ^ -(n & 1);
  }

  /**
   * Decode a ZigZag-encoded 64-bit value. ZigZag encodes signed integers into values that can be
   * efficiently encoded with varint. (Otherwise, negative values must be sign-extended to 64 bits
   * to be varint encoded, thus always taking 10 bytes on the wire.)
   *
   * @param n An unsigned 64-bit integer, stored in a signed int because Java has no explicit
   *     unsigned support.
   * @return A signed 64-bit integer.
   */
  public static long decodeZigZag64(final long n) {
    return (n >>> 1) ^ -(n & 1);
  }

  /**
   * Like {@link #readRawVarint32(InputStream)}, but expects that the caller has already read one
   * byte. This allows the caller to determine if EOF has been reached before attempting to read.
   */
  public static int readRawVarint32(final int firstByte, final InputStream input)
      throws IOException {
    if ((firstByte & 0x80) == 0) {
      return firstByte;
    }

    int result = firstByte & 0x7f;
    int offset = 7;
    for (; offset < 32; offset += 7) {
      final int b = input.read();
      if (b == -1) {
        throw InvalidProtocolBufferException.truncatedMessage();
      }
      result |= (b & 0x7f) << offset;
      if ((b & 0x80) == 0) {
        return result;
      }
    }
    // Keep reading up to 64 bits.
    for (; offset < 64; offset += 7) {
      final int b = input.read();
      if (b == -1) {
        throw InvalidProtocolBufferException.truncatedMessage();
      }
      if ((b & 0x80) == 0) {
        return result;
      }
    }
    throw InvalidProtocolBufferException.malformedVarint();
  }

  /**
   * Reads a varint from the input one byte at a time, so that it does not read any bytes after the
   * end of the varint. If you simply wrapped the stream in a CodedInputStream and used {@link
   * #readRawVarint32(InputStream)} then you would probably end up reading past the end of the
   * varint since CodedInputStream buffers its input.
   */
  static int readRawVarint32(final InputStream input) throws IOException {
    final int firstByte = input.read();
    if (firstByte == -1) {
      throw InvalidProtocolBufferException.truncatedMessage();
    }
    return readRawVarint32(firstByte, input);
  }
}
