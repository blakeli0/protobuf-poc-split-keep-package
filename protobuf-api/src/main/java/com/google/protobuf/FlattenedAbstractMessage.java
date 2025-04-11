// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

package com.google.protobuf;

import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.Internal.EnumLite;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.google.protobuf.Internal.checkNotNull;

/**
 * A partial implementation of the {@link Message} interface which implements as many methods of
 * that interface as possible in terms of other methods.
 *
 * @author kenton@google.com Kenton Varda
 */
public abstract class FlattenedAbstractMessage
    // TODO: Update GeneratedMessage to parameterize with MessageType and BuilderType.
 implements Message {

  protected int memoizedHashCode = 0;

  @Override
  public ByteString toByteString() {
    try {
      final ByteString.CodedBuilder out = ByteString.newCodedBuilder(getSerializedSize());
      writeTo(out.getCodedOutput());
      return out.build();
    } catch (IOException e) {
      throw new RuntimeException(getSerializingExceptionMessage("ByteString"), e);
    }
  }

  @Override
  public byte[] toByteArray() {
    try {
      final byte[] result = new byte[getSerializedSize()];
      final CodedOutputStream output = CodedOutputStream.newInstance(result);
      writeTo(output);
      output.checkNoSpaceLeft();
      return result;
    } catch (IOException e) {
      throw new RuntimeException(getSerializingExceptionMessage("byte array"), e);
    }
  }

  @Override
  public void writeTo(final OutputStream output) throws IOException {
    final int bufferSize = CodedOutputStream.computePreferredBufferSize(getSerializedSize());
    final CodedOutputStream codedOutput = CodedOutputStream.newInstance(output, bufferSize);
    writeTo(codedOutput);
    codedOutput.flush();
  }

  @Override
  public void writeDelimitedTo(final OutputStream output) throws IOException {
    final int serialized = getSerializedSize();
    final int bufferSize =
            CodedOutputStream.computePreferredBufferSize(
                    CodedOutputStream.computeUInt32SizeNoTag(serialized) + serialized);
    final CodedOutputStream codedOutput = CodedOutputStream.newInstance(output, bufferSize);
    codedOutput.writeUInt32NoTag(serialized);
    writeTo(codedOutput);
    codedOutput.flush();
  }

  int getSerializedSize(
          Schema schema) {
    int memoizedSerializedSize = getMemoizedSerializedSize();
    if (memoizedSerializedSize == -1) {
      memoizedSerializedSize = schema.getSerializedSize(this);
      setMemoizedSerializedSize(memoizedSerializedSize);
    }
    return memoizedSerializedSize;
  }

  private String getSerializingExceptionMessage(String target) {
    return "Serializing "
            + getClass().getName()
            + " to a "
            + target
            + " threw an IOException (should never happen).";
  }

  protected static void checkByteStringIsUtf8(ByteString byteString)
          throws IllegalArgumentException {
    if (!byteString.isValidUtf8()) {
      throw new IllegalArgumentException("Byte string is not UTF-8.");
    }
  }

  /** Interface for an enum which signifies which field in a {@code oneof} was specified. */
  protected interface InternalOneOfEnum {
    /**
     * Retrieves the field number of the field which was set in this {@code oneof}, or {@code 0} if
     * none were.
     */
    int getNumber();
  }

  @Override
  public boolean isInitialized() {
    return MessageReflection.isInitialized(this);
  }

  /**
   * Interface for the parent of a Builder that allows the builder to communicate invalidations back
   * to the parent for use when using nested builders.
   */
  protected interface BuilderParent {

    /**
     * A builder becomes dirty whenever a field is modified -- including fields in nested builders
     * -- and becomes clean when build() is called. Thus, when a builder becomes dirty, all its
     * parents become dirty as well, and when it becomes clean, all its children become clean. The
     * dirtiness state is used to invalidate certain cached values.
     *
     * <p>To this end, a builder calls markDirty() on its parent whenever it transitions from clean
     * to dirty. The parent must propagate this call to its own parent, unless it was already dirty,
     * in which case the grandparent must necessarily already be dirty as well. The parent can only
     * transition back to "clean" after calling build() on all children.
     */
    void markDirty();
  }

  /** Create a nested builder. */
  protected Message.Builder newBuilderForType(BuilderParent parent) {
    throw new UnsupportedOperationException("Nested builder is not supported for this type.");
  }

  @Override
  public List<String> findInitializationErrors() {
    return MessageReflection.findMissingFields(this);
  }

  @Override
  public String getInitializationErrorString() {
    return MessageReflection.delimitWithCommas(findInitializationErrors());
  }

  // TODO: Clear it when all subclasses have implemented this method.
  @Override
  public boolean hasOneof(OneofDescriptor oneof) {
    throw new UnsupportedOperationException("hasOneof() is not implemented.");
  }

  // TODO: Clear it when all subclasses have implemented this method.
  @Override
  public FieldDescriptor getOneofFieldDescriptor(OneofDescriptor oneof) {
    throw new UnsupportedOperationException("getOneofFieldDescriptor() is not implemented.");
  }

  @Override
  public final String toString() {
    return TextFormat.printer().printToString(this);
  }

  @Override
  public void writeTo(final CodedOutputStream output) throws IOException {
    MessageReflection.writeMessageTo(this, getAllFields(), output, false);
  }

  protected int memoizedSize = -1;

  int getMemoizedSerializedSize() {
    return memoizedSize;
  }

  void setMemoizedSerializedSize(int size) {
    memoizedSize = size;
  }

  @Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) {
      return size;
    }

    memoizedSize = MessageReflection.getSerializedSize(this, getAllFields());
    return memoizedSize;
  }

  @Override
  public boolean equals(final Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Message)) {
      return false;
    }
    final Message otherMessage = (Message) other;
    if (getDescriptorForType() != otherMessage.getDescriptorForType()) {
      return false;
    }
    return compareFields(getAllFields(), otherMessage.getAllFields())
        && getUnknownFields().equals(otherMessage.getUnknownFields());
  }

  @Override
  public int hashCode() {
    int hash = memoizedHashCode;
    if (hash == 0) {
      hash = 41;
      hash = (19 * hash) + getDescriptorForType().hashCode();
      hash = hashFields(hash, getAllFields());
      hash = (29 * hash) + getUnknownFields().hashCode();
      memoizedHashCode = hash;
    }
    return hash;
  }

  private static ByteString toByteString(Object value) {
    if (value instanceof byte[]) {
      return ByteString.copyFrom((byte[]) value);
    } else {
      return (ByteString) value;
    }
  }

  /**
   * Compares two bytes fields. The parameters must be either a byte array or a ByteString object.
   * They can be of different type though.
   */
  private static boolean compareBytes(Object a, Object b) {
    if (a instanceof byte[] && b instanceof byte[]) {
      return Arrays.equals((byte[]) a, (byte[]) b);
    }
    return toByteString(a).equals(toByteString(b));
  }

  /** Converts a list of MapEntry messages into a Map used for equals() and hashCode(). */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Map convertMapEntryListToMap(List list) {
    if (list.isEmpty()) {
      return Collections.emptyMap();
    }
    Map result = new HashMap<>();
    Iterator iterator = list.iterator();
    Message entry = (Message) iterator.next();
    Descriptors.Descriptor descriptor = entry.getDescriptorForType();
    FieldDescriptor key = descriptor.findFieldByName("key");
    FieldDescriptor value = descriptor.findFieldByName("value");
    Object fieldValue = entry.getField(value);
    if (fieldValue instanceof EnumValueDescriptor) {
      fieldValue = ((EnumValueDescriptor) fieldValue).getNumber();
    }
    result.put(entry.getField(key), fieldValue);
    while (iterator.hasNext()) {
      entry = (Message) iterator.next();
      fieldValue = entry.getField(value);
      if (fieldValue instanceof EnumValueDescriptor) {
        fieldValue = ((EnumValueDescriptor) fieldValue).getNumber();
      }
      result.put(entry.getField(key), fieldValue);
    }
    return result;
  }

  /** Compares two map fields. The parameters must be a list of MapEntry messages. */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static boolean compareMapField(Object a, Object b) {
    Map ma = convertMapEntryListToMap((List) a);
    Map mb = convertMapEntryListToMap((List) b);
    return MapFieldLite.equals(ma, mb);
  }

  /**
   * Compares two sets of fields. This method is used to implement {@link
   * FlattenedAbstractMessage#equals(Object)} and {@link AbstractMutableMessage#equals(Object)}. It takes
   * special care of bytes fields because immutable messages and mutable messages use different Java
   * type to represent a bytes field and this method should be able to compare immutable messages,
   * mutable messages and also an immutable message to a mutable message.
   */
  static boolean compareFields(Map<FieldDescriptor, Object> a, Map<FieldDescriptor, Object> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (FieldDescriptor descriptor : a.keySet()) {
      if (!b.containsKey(descriptor)) {
        return false;
      }
      Object value1 = a.get(descriptor);
      Object value2 = b.get(descriptor);
      if (descriptor.getType() == FieldDescriptor.Type.BYTES) {
        if (descriptor.isRepeated()) {
          List<?> list1 = (List) value1;
          List<?> list2 = (List) value2;
          if (list1.size() != list2.size()) {
            return false;
          }
          for (int i = 0; i < list1.size(); i++) {
            if (!compareBytes(list1.get(i), list2.get(i))) {
              return false;
            }
          }
        } else {
          // Compares a singular bytes field.
          if (!compareBytes(value1, value2)) {
            return false;
          }
        }
      } else if (descriptor.isMapField()) {
        if (!compareMapField(value1, value2)) {
          return false;
        }
      } else {
        // Compare non-bytes fields.
        if (!value1.equals(value2)) {
          return false;
        }
      }
    }
    return true;
  }

  /** Calculates the hash code of a map field. {@code value} must be a list of MapEntry messages. */
  @SuppressWarnings("unchecked")
  private static int hashMapField(Object value) {
    return MapFieldLite.calculateHashCodeForMap(convertMapEntryListToMap((List) value));
  }

  /** Get a hash code for given fields and values, using the given seed. */
  @SuppressWarnings("unchecked")
  protected static int hashFields(int hash, Map<FieldDescriptor, Object> map) {
    for (Map.Entry<FieldDescriptor, Object> entry : map.entrySet()) {
      FieldDescriptor field = entry.getKey();
      Object value = entry.getValue();
      hash = (37 * hash) + field.getNumber();
      if (field.isMapField()) {
        hash = (53 * hash) + hashMapField(value);
      } else if (field.getType() != FieldDescriptor.Type.ENUM) {
        hash = (53 * hash) + value.hashCode();
      } else if (field.isRepeated()) {
        List<? extends EnumLite> list = (List<? extends EnumLite>) value;
        hash = (53 * hash) + Internal.hashEnumList(list);
      } else {
        hash = (53 * hash) + Internal.hashEnum((EnumLite) value);
      }
    }
    return hash;
  }

  /**
   * Package private helper method for AbstractParser to create UninitializedMessageException with
   * missing field information.
   */
  UninitializedMessageException newUninitializedMessageException() {
    return Builder.newUninitializedMessageException(this);
  }

  // =================================================================

  /**
   * A partial implementation of the {@link Message.Builder} interface which implements as many
   * methods of that interface as possible in terms of other methods.
   */
  @SuppressWarnings("unchecked")
  public abstract static class Builder<BuilderType extends Builder<BuilderType>> implements Message.Builder {

    /**
     * An InputStream implementations which reads from some other InputStream but is limited to a
     * particular number of bytes. Used by mergeDelimitedFrom(). This is intentionally
     * package-private so that UnknownFieldSet can share it.
     */
    static final class LimitedInputStream extends FilterInputStream {
      private int limit;

      LimitedInputStream(InputStream in, int limit) {
        super(in);
        this.limit = limit;
      }

      @Override
      public int available() throws IOException {
        return Math.min(super.available(), limit);
      }

      @Override
      public int read() throws IOException {
        if (limit <= 0) {
          return -1;
        }
        final int result = super.read();
        if (result >= 0) {
          --limit;
        }
        return result;
      }

      @Override
      public int read(final byte[] b, final int off, int len) throws IOException {
        if (limit <= 0) {
          return -1;
        }
        len = Math.min(len, limit);
        final int result = super.read(b, off, len);
        if (result >= 0) {
          limit -= result;
        }
        return result;
      }

      @Override
      public long skip(final long n) throws IOException {
        // because we take the minimum of an int and a long, result is guaranteed to be
        // less than or equal to Integer.MAX_INT so this cast is safe
        int result = (int) super.skip(Math.min(n, limit));
        if (result >= 0) {
          // if the superclass adheres to the contract for skip, this condition is always true
          limit -= result;
        }
        return result;
      }
    }

    @Override
    public boolean mergeDelimitedFrom(
            final InputStream input, final ExtensionRegistryLite extensionRegistry) throws IOException {
      final int firstByte = input.read();
      if (firstByte == -1) {
        return false;
      }
      final int size = CodedInputStream.readRawVarint32(firstByte, input);
      final InputStream limitedInput = new LimitedInputStream(input, size);
      mergeFrom(limitedInput, extensionRegistry);
      return true;
    }

    @Override
    public boolean mergeDelimitedFrom(final InputStream input) throws IOException {
      return mergeDelimitedFrom(input, ExtensionRegistryLite.getEmptyRegistry());
    }

    @Override
    @SuppressWarnings("unchecked") // isInstance takes care of this
    public BuilderType mergeFrom(final MessageLite other) {
      if (!getDefaultInstanceForType().getClass().isInstance(other)) {
        throw new IllegalArgumentException(
                "mergeFrom(MessageLite) can only merge messages of the same type.");
      }

      return internalMergeFrom(other);
    }

    private String getReadingExceptionMessage(String target) {
      return "Reading "
              + getClass().getName()
              + " from a "
              + target
              + " threw an IOException (should never happen).";
    }

    // We check nulls as we iterate to avoid iterating over values twice.
    private static <T> void addAllCheckingNulls(Iterable<T> values, List<? super T> list) {
      if (list instanceof ArrayList && values instanceof Collection) {
        ((ArrayList<T>) list).ensureCapacity(list.size() + ((Collection<T>) values).size());
      }
      int begin = list.size();
      for (T value : values) {
        if (value == null) {
          // encountered a null value so we must undo our modifications prior to throwing
          String message = "Element at index " + (list.size() - begin) + " is null.";
          for (int i = list.size() - 1; i >= begin; i--) {
            list.remove(i);
          }
          throw new NullPointerException(message);
        }
        list.add(value);
      }
    }

    /** Construct an UninitializedMessageException reporting missing fields in the given message. */
    protected static UninitializedMessageException newUninitializedMessageException(
            MessageLite message) {
      return new UninitializedMessageException(message);
    }

    // For binary compatibility.
    @Deprecated
    protected static <T> void addAll(final Iterable<T> values, final Collection<? super T> list) {
      addAll(values, (List<T>) list);
    }

    /**
     * Adds the {@code values} to the {@code list}. This is a helper method used by generated code.
     * Users should ignore it.
     *
     * @throws NullPointerException if {@code values} or any of the elements of {@code values} is
     *     null.
     */
    protected static <T> void addAll(final Iterable<T> values, final List<? super T> list) {
      checkNotNull(values);
      if (values instanceof LazyStringList) {
        // For StringOrByteStringLists, check the underlying elements to avoid
        // forcing conversions of ByteStrings to Strings.
        // TODO: Could we just prohibit nulls in all protobuf lists and get rid of this? Is
        // if even possible to hit this condition as all protobuf methods check for null first,
        // right?
        List<?> lazyValues = ((LazyStringList) values).getUnderlyingElements();
        LazyStringList lazyList = (LazyStringList) list;
        int begin = list.size();
        for (Object value : lazyValues) {
          if (value == null) {
            // encountered a null value so we must undo our modifications prior to throwing
            String message = "Element at index " + (lazyList.size() - begin) + " is null.";
            for (int i = lazyList.size() - 1; i >= begin; i--) {
              lazyList.remove(i);
            }
            throw new NullPointerException(message);
          }
          if (value instanceof ByteString) {
            lazyList.add((ByteString) value);
          } else {
            lazyList.add((String) value);
          }
        }
      } else {
        if (values instanceof PrimitiveNonBoxingCollection) {
          list.addAll((Collection<T>) values);
        } else {
          addAllCheckingNulls(values, list);
        }
      }
    }

    // The compiler produces an error if this is not declared explicitly.
    // Method isn't abstract to bypass Java 1.6 compiler issue:
    //     http://bugs.java.com/view_bug.do?bug_id=6908259
    @Override
    public BuilderType clone() {
      throw new UnsupportedOperationException("clone() should be implemented in subclasses.");
    }

    /** TODO: Clear it when all subclasses have implemented this method. */
    @Override
    public boolean hasOneof(OneofDescriptor oneof) {
      throw new UnsupportedOperationException("hasOneof() is not implemented.");
    }

    /** TODO: Clear it when all subclasses have implemented this method. */
    @Override
    public FieldDescriptor getOneofFieldDescriptor(OneofDescriptor oneof) {
      throw new UnsupportedOperationException("getOneofFieldDescriptor() is not implemented.");
    }

    /** TODO: Clear it when all subclasses have implemented this method. */
    @Override
    public BuilderType clearOneof(OneofDescriptor oneof) {
      throw new UnsupportedOperationException("clearOneof() is not implemented.");
    }

    @Override
    public BuilderType clear() {
      for (final Map.Entry<FieldDescriptor, Object> entry : getAllFields().entrySet()) {
        clearField(entry.getKey());
      }
      return (BuilderType) this;
    }

    @Override
    public List<String> findInitializationErrors() {
      return MessageReflection.findMissingFields(this);
    }

    @Override
    public String getInitializationErrorString() {
      return MessageReflection.delimitWithCommas(findInitializationErrors());
    }

    protected BuilderType internalMergeFrom(MessageLite other) {
      return mergeFrom((Message) other);
    }

    @Override
    public BuilderType mergeFrom(final Message other) {
      return mergeFrom(other, other.getAllFields());
    }

    BuilderType mergeFrom(final Message other, Map<FieldDescriptor, Object> allFields) {
      if (other.getDescriptorForType() != getDescriptorForType()) {
        throw new IllegalArgumentException(
            "mergeFrom(Message) can only merge messages of the same type.");
      }

      // Note:  We don't attempt to verify that other's fields have valid
      //   types.  Doing so would be a losing battle.  We'd have to verify
      //   all sub-messages as well, and we'd have to make copies of all of
      //   them to insure that they don't change after verification (since
      //   the Message interface itself cannot enforce immutability of
      //   implementations).

      for (final Map.Entry<FieldDescriptor, Object> entry : allFields.entrySet()) {
        final FieldDescriptor field = entry.getKey();
        if (field.isRepeated()) {
          for (final Object element : (List) entry.getValue()) {
            addRepeatedField(field, element);
          }
        } else if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
          final Message existingValue = (Message) getField(field);
          if (existingValue == existingValue.getDefaultInstanceForType()) {
            setField(field, entry.getValue());
          } else {
            setField(
                field,
                existingValue
                    .newBuilderForType()
                    .mergeFrom(existingValue)
                    .mergeFrom((Message) entry.getValue())
                    .build());
          }
        } else {
          setField(field, entry.getValue());
        }
      }

      mergeUnknownFields(other.getUnknownFields());

      return (BuilderType) this;
    }

    @Override
    public BuilderType mergeFrom(final CodedInputStream input) throws IOException {
      return mergeFrom(input, ExtensionRegistry.getEmptyRegistry());
    }

    @Override
    public BuilderType mergeFrom(
        final CodedInputStream input, final ExtensionRegistryLite extensionRegistry)
        throws IOException {
      boolean discardUnknown = input.shouldDiscardUnknownFields();
      final UnknownFieldSet.Builder unknownFields =
          discardUnknown ? null : getUnknownFieldSetBuilder();
      MessageReflection.mergeMessageFrom(this, unknownFields, input, extensionRegistry);
      if (unknownFields != null) {
        setUnknownFieldSetBuilder(unknownFields);
      }
      return (BuilderType) this;
    }

    protected UnknownFieldSet.Builder getUnknownFieldSetBuilder() {
      return UnknownFieldSet.newBuilder(getUnknownFields());
    }

    protected void setUnknownFieldSetBuilder(final UnknownFieldSet.Builder builder) {
      setUnknownFields(builder.build());
    }

    @Override
    public BuilderType mergeUnknownFields(final UnknownFieldSet unknownFields) {
      setUnknownFields(
          UnknownFieldSet.newBuilder(getUnknownFields()).mergeFrom(unknownFields).build());
      return (BuilderType) this;
    }

    @Override
    public Message.Builder getFieldBuilder(final FieldDescriptor field) {
      throw new UnsupportedOperationException(
          "getFieldBuilder() called on an unsupported message type.");
    }

    @Override
    public Message.Builder getRepeatedFieldBuilder(final FieldDescriptor field, int index) {
      throw new UnsupportedOperationException(
          "getRepeatedFieldBuilder() called on an unsupported message type.");
    }

    @Override
    public String toString() {
      return TextFormat.printer().printToString(this);
    }

    /** Construct an UninitializedMessageException reporting missing fields in the given message. */
    protected static UninitializedMessageException newUninitializedMessageException(
        Message message) {
      return new UninitializedMessageException(MessageReflection.findMissingFields(message));
    }

    /**
     * Used to support nested builders and called to mark this builder as clean. Clean builders will
     * propagate the {@link BuilderParent#markDirty()} event to their parent builders, while dirty
     * builders will not, as their parents should be dirty already.
     *
     * <p>NOTE: Implementations that don't support nested builders don't need to override this
     * method.
     */
    void markClean() {
      throw new IllegalStateException("Should be overridden by subclasses.");
    }

    /**
     * Used to support nested builders and called when this nested builder is no longer used by its
     * parent builder and should release the reference to its parent builder.
     *
     * <p>NOTE: Implementations that don't support nested builders don't need to override this
     * method.
     */
    void dispose() {
      throw new IllegalStateException("Should be overridden by subclasses.");
    }

    // ===============================================================
    // The following definitions seem to be required in order to make javac
    // not produce weird errors like:
    //
    // java/com/google/protobuf/DynamicMessage.java:203: types
    //   com.google.protobuf.AbstractMessage.Builder<
    //     com.google.protobuf.DynamicMessage.Builder> and
    //   com.google.protobuf.AbstractMessage.Builder<
    //     com.google.protobuf.DynamicMessage.Builder> are incompatible; both
    //   define mergeFrom(com.google.protobuf.ByteString), but with unrelated
    //   return types.
    //
    // Strangely, these lines are only needed if javac is invoked separately
    // on AbstractMessage.java and AbstractMessageLite.java.  If javac is
    // invoked on both simultaneously, it works.  (Or maybe the important
    // point is whether or not DynamicMessage.java is compiled together with
    // AbstractMessageLite.java -- not sure.)  I suspect this is a compiler
    // bug.

    public BuilderType mergeFrom(final ByteString data) throws InvalidProtocolBufferException {
      try {
        final CodedInputStream input = data.newCodedInput();
        mergeFrom(input);
        input.checkLastTagWas(0);
        return (BuilderType) this;
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (IOException e) {
        throw new RuntimeException(getReadingExceptionMessage("ByteString"), e);
      }
    }

    public BuilderType mergeFrom(
            final ByteString data, final ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
      try {
        final CodedInputStream input = data.newCodedInput();
        mergeFrom(input, extensionRegistry);
        input.checkLastTagWas(0);
        return (BuilderType) this;
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (IOException e) {
        throw new RuntimeException(getReadingExceptionMessage("ByteString"), e);
      }
    }

    public BuilderType mergeFrom(final byte[] data) throws InvalidProtocolBufferException {
      return mergeFrom(data, 0, data.length);
    }

    public BuilderType mergeFrom(final byte[] data, final int off, final int len)
            throws InvalidProtocolBufferException {
      try {
        final CodedInputStream input = CodedInputStream.newInstance(data, off, len);
        mergeFrom(input);
        input.checkLastTagWas(0);
        return (BuilderType) this;
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (IOException e) {
        throw new RuntimeException(getReadingExceptionMessage("byte array"), e);
      }
    }

    public BuilderType mergeFrom(final byte[] data, final ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
      return mergeFrom(data, 0, data.length, extensionRegistry);
    }

    public BuilderType mergeFrom(
            final byte[] data,
            final int off,
            final int len,
            final ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
      try {
        final CodedInputStream input = CodedInputStream.newInstance(data, off, len);
        mergeFrom(input, extensionRegistry);
        input.checkLastTagWas(0);
        return (BuilderType) this;
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (IOException e) {
        throw new RuntimeException(getReadingExceptionMessage("byte array"), e);
      }
    }

    public BuilderType mergeFrom(final InputStream input) throws IOException {
      final CodedInputStream codedInput = CodedInputStream.newInstance(input);
      mergeFrom(codedInput);
      codedInput.checkLastTagWas(0);
      return (BuilderType) this;
    }

    public BuilderType mergeFrom(
            final InputStream input, final ExtensionRegistryLite extensionRegistry) throws IOException {
      final CodedInputStream codedInput = CodedInputStream.newInstance(input);
      mergeFrom(codedInput, extensionRegistry);
      codedInput.checkLastTagWas(0);
      return (BuilderType) this;
    }

  /**
   * @deprecated from v3.0.0-beta-3+, for compatibility with v2.5.0 and v2.6.1
   * generated code.
   */
  @Deprecated
  protected static int hashLong(long n) {
    return (int) (n ^ (n >>> 32));
  }

  /**
   * @deprecated from v3.0.0-beta-3+, for compatibility with v2.5.0 and v2.6.1
   * generated code.
   */
  @Deprecated
  protected static int hashBoolean(boolean b) {
    return b ? 1231 : 1237;
  }

  /**
   * @deprecated from v3.0.0-beta-3+, for compatibility with v2.5.0 and v2.6.1
   * generated code.
   */
  @Deprecated
  protected static int hashEnum(EnumLite e) {
    return e.getNumber();
  }

  /**
   * @deprecated from v3.0.0-beta-3+, for compatibility with v2.5.0 and v2.6.1
   * generated code.
   */
  @Deprecated
  protected static int hashEnumList(List<? extends EnumLite> list) {
    int hash = 1;
    for (EnumLite e : list) {
      hash = 31 * hash + hashEnum(e);
    }
    return hash;
  }
}
}
