package com.google.protobuf;

import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * A serialized (serializable) form of the generated message. Stores the message as a class name
 * and a byte array.
 */
final class SerializedForm implements Serializable {

    public static SerializedForm of(MessageLite message) {
        return new SerializedForm(message);
    }

    private static final long serialVersionUID = 0L;

    // since v3.6.1
    private final Class<?> messageClass;
    private final String messageClassName;
    private final byte[] asBytes;

    /**
     * Creates the serialized form by calling {@link com.google.protobuf.MessageLite#toByteArray}.
     *
     * @param regularForm the message to serialize
     */
    SerializedForm(MessageLite regularForm) {
        messageClass = regularForm.getClass();
        messageClassName = regularForm.getClass().getName();
        asBytes = regularForm.toByteArray();
    }

    /**
     * When read from an ObjectInputStream, this method converts this object back to the regular
     * form. Part of Java's serialization magic.
     *
     * @return a GeneratedMessage of the type that was serialized
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            Class<?> messageClass = resolveMessageClass();
            java.lang.reflect.Field defaultInstanceField =
                    messageClass.getDeclaredField("DEFAULT_INSTANCE");
            defaultInstanceField.setAccessible(true);
            MessageLite defaultInstance = (MessageLite) defaultInstanceField.get(null);
            return defaultInstance.newBuilderForType().mergeFrom(asBytes).buildPartial();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to find proto buffer class: " + messageClassName, e);
        } catch (NoSuchFieldException e) {
            return readResolveFallback();
        } catch (SecurityException e) {
            throw new RuntimeException("Unable to call DEFAULT_INSTANCE in " + messageClassName, e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to call parsePartialFrom", e);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Unable to understand proto buffer", e);
        }
    }

    /**
     * @deprecated from v3.0.0-beta-3+, for compatibility with v2.5.0 and v2.6.1 generated code.
     */
    @Deprecated
    private Object readResolveFallback() throws ObjectStreamException {
        try {
            Class<?> messageClass = resolveMessageClass();
            java.lang.reflect.Field defaultInstanceField =
                    messageClass.getDeclaredField("defaultInstance");
            defaultInstanceField.setAccessible(true);
            MessageLite defaultInstance = (MessageLite) defaultInstanceField.get(null);
            return defaultInstance.newBuilderForType()
                    .mergeFrom(asBytes)
                    .buildPartial();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to find proto buffer class: " + messageClassName, e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Unable to find defaultInstance in " + messageClassName, e);
        } catch (SecurityException e) {
            throw new RuntimeException("Unable to call defaultInstance in " + messageClassName, e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to call parsePartialFrom", e);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Unable to understand proto buffer", e);
        }
    }

    private Class<?> resolveMessageClass() throws ClassNotFoundException {
        return messageClass != null ? messageClass : Class.forName(messageClassName);
    }
}