// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/protobuf/struct.proto

// Protobuf Java Version: 3.25.5
package com.google.protobuf;

public final class StructProto {
  private StructProto() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_google_protobuf_Struct_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_google_protobuf_Struct_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_google_protobuf_Struct_FieldsEntry_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_google_protobuf_Struct_FieldsEntry_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_google_protobuf_Value_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_google_protobuf_Value_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_google_protobuf_ListValue_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_google_protobuf_ListValue_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\034google/protobuf/struct.proto\022\017google.p" +
      "rotobuf\"\204\001\n\006Struct\0223\n\006fields\030\001 \003(\0132#.goo" +
      "gle.protobuf.Struct.FieldsEntry\032E\n\013Field" +
      "sEntry\022\013\n\003key\030\001 \001(\t\022%\n\005value\030\002 \001(\0132\026.goo" +
      "gle.protobuf.Value:\0028\001\"\352\001\n\005Value\0220\n\nnull" +
      "_value\030\001 \001(\0162\032.google.protobuf.NullValue" +
      "H\000\022\026\n\014number_value\030\002 \001(\001H\000\022\026\n\014string_val" +
      "ue\030\003 \001(\tH\000\022\024\n\nbool_value\030\004 \001(\010H\000\022/\n\014stru" +
      "ct_value\030\005 \001(\0132\027.google.protobuf.StructH" +
      "\000\0220\n\nlist_value\030\006 \001(\0132\032.google.protobuf." +
      "ListValueH\000B\006\n\004kind\"3\n\tListValue\022&\n\006valu" +
      "es\030\001 \003(\0132\026.google.protobuf.Value*\033\n\tNull" +
      "Value\022\016\n\nNULL_VALUE\020\000B\177\n\023com.google.prot" +
      "obufB\013StructProtoP\001Z/google.golang.org/p" +
      "rotobuf/types/known/structpb\370\001\001\242\002\003GPB\252\002\036" +
      "Google.Protobuf.WellKnownTypesb\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_google_protobuf_Struct_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_google_protobuf_Struct_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_google_protobuf_Struct_descriptor,
        new java.lang.String[] { "Fields", });
    internal_static_google_protobuf_Struct_FieldsEntry_descriptor =
      internal_static_google_protobuf_Struct_descriptor.getNestedTypes().get(0);
    internal_static_google_protobuf_Struct_FieldsEntry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_google_protobuf_Struct_FieldsEntry_descriptor,
        new java.lang.String[] { "Key", "Value", });
    internal_static_google_protobuf_Value_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_google_protobuf_Value_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_google_protobuf_Value_descriptor,
        new java.lang.String[] { "NullValue", "NumberValue", "StringValue", "BoolValue", "StructValue", "ListValue", "Kind", });
    internal_static_google_protobuf_ListValue_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_google_protobuf_ListValue_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_google_protobuf_ListValue_descriptor,
        new java.lang.String[] { "Values", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
