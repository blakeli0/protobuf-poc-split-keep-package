// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/protobuf/api.proto

// Protobuf Java Version: 3.25.5
package com.google.protobuf;

public interface MixinOrBuilder extends
    // @@protoc_insertion_point(interface_extends:google.protobuf.Mixin)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * The fully qualified name of the interface which is included.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The name.
   */
  java.lang.String getName();
  /**
   * <pre>
   * The fully qualified name of the interface which is included.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The bytes for name.
   */
  com.google.protobuf.ByteString
      getNameBytes();

  /**
   * <pre>
   * If non-empty specifies a path under which inherited HTTP paths
   * are rooted.
   * </pre>
   *
   * <code>string root = 2;</code>
   * @return The root.
   */
  java.lang.String getRoot();
  /**
   * <pre>
   * If non-empty specifies a path under which inherited HTTP paths
   * are rooted.
   * </pre>
   *
   * <code>string root = 2;</code>
   * @return The bytes for root.
   */
  com.google.protobuf.ByteString
      getRootBytes();
}
