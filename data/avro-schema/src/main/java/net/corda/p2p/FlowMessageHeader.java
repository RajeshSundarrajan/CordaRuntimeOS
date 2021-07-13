/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package net.corda.p2p;

import org.apache.avro.generic.GenericArray;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@org.apache.avro.specific.AvroGenerated
public class FlowMessageHeader extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 5313040266524727775L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"FlowMessageHeader\",\"namespace\":\"net.corda.p2p\",\"fields\":[{\"name\":\"destination\",\"type\":{\"type\":\"record\",\"name\":\"HoldingIdentity\",\"fields\":[{\"name\":\"x500Name\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"groupId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}},{\"name\":\"source\",\"type\":\"HoldingIdentity\"},{\"name\":\"ttl\",\"type\":[\"null\",\"long\"]},{\"name\":\"messageId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"traceId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<FlowMessageHeader> ENCODER =
      new BinaryMessageEncoder<FlowMessageHeader>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<FlowMessageHeader> DECODER =
      new BinaryMessageDecoder<FlowMessageHeader>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<FlowMessageHeader> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<FlowMessageHeader> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<FlowMessageHeader> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<FlowMessageHeader>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this FlowMessageHeader to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a FlowMessageHeader from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a FlowMessageHeader instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static FlowMessageHeader fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private net.corda.p2p.HoldingIdentity destination;
   private net.corda.p2p.HoldingIdentity source;
   private java.lang.Long ttl;
   private java.lang.String messageId;
   private java.lang.String traceId;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public FlowMessageHeader() {}

  /**
   * All-args constructor.
   * @param destination The new value for destination
   * @param source The new value for source
   * @param ttl The new value for ttl
   * @param messageId The new value for messageId
   * @param traceId The new value for traceId
   */
  public FlowMessageHeader(net.corda.p2p.HoldingIdentity destination, net.corda.p2p.HoldingIdentity source, java.lang.Long ttl, java.lang.String messageId, java.lang.String traceId) {
    this.destination = destination;
    this.source = source;
    this.ttl = ttl;
    this.messageId = messageId;
    this.traceId = traceId;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return destination;
    case 1: return source;
    case 2: return ttl;
    case 3: return messageId;
    case 4: return traceId;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: destination = (net.corda.p2p.HoldingIdentity)value$; break;
    case 1: source = (net.corda.p2p.HoldingIdentity)value$; break;
    case 2: ttl = (java.lang.Long)value$; break;
    case 3: messageId = value$ != null ? value$.toString() : null; break;
    case 4: traceId = value$ != null ? value$.toString() : null; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'destination' field.
   * @return The value of the 'destination' field.
   */
  public net.corda.p2p.HoldingIdentity getDestination() {
    return destination;
  }


  /**
   * Sets the value of the 'destination' field.
   * @param value the value to set.
   */
  public void setDestination(net.corda.p2p.HoldingIdentity value) {
    this.destination = value;
  }

  /**
   * Gets the value of the 'source' field.
   * @return The value of the 'source' field.
   */
  public net.corda.p2p.HoldingIdentity getSource() {
    return source;
  }


  /**
   * Sets the value of the 'source' field.
   * @param value the value to set.
   */
  public void setSource(net.corda.p2p.HoldingIdentity value) {
    this.source = value;
  }

  /**
   * Gets the value of the 'ttl' field.
   * @return The value of the 'ttl' field.
   */
  public java.lang.Long getTtl() {
    return ttl;
  }


  /**
   * Sets the value of the 'ttl' field.
   * @param value the value to set.
   */
  public void setTtl(java.lang.Long value) {
    this.ttl = value;
  }

  /**
   * Gets the value of the 'messageId' field.
   * @return The value of the 'messageId' field.
   */
  public java.lang.String getMessageId() {
    return messageId;
  }


  /**
   * Sets the value of the 'messageId' field.
   * @param value the value to set.
   */
  public void setMessageId(java.lang.String value) {
    this.messageId = value;
  }

  /**
   * Gets the value of the 'traceId' field.
   * @return The value of the 'traceId' field.
   */
  public java.lang.String getTraceId() {
    return traceId;
  }


  /**
   * Sets the value of the 'traceId' field.
   * @param value the value to set.
   */
  public void setTraceId(java.lang.String value) {
    this.traceId = value;
  }

  /**
   * Creates a new FlowMessageHeader RecordBuilder.
   * @return A new FlowMessageHeader RecordBuilder
   */
  public static net.corda.p2p.FlowMessageHeader.Builder newBuilder() {
    return new net.corda.p2p.FlowMessageHeader.Builder();
  }

  /**
   * Creates a new FlowMessageHeader RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new FlowMessageHeader RecordBuilder
   */
  public static net.corda.p2p.FlowMessageHeader.Builder newBuilder(net.corda.p2p.FlowMessageHeader.Builder other) {
    if (other == null) {
      return new net.corda.p2p.FlowMessageHeader.Builder();
    } else {
      return new net.corda.p2p.FlowMessageHeader.Builder(other);
    }
  }

  /**
   * Creates a new FlowMessageHeader RecordBuilder by copying an existing FlowMessageHeader instance.
   * @param other The existing instance to copy.
   * @return A new FlowMessageHeader RecordBuilder
   */
  public static net.corda.p2p.FlowMessageHeader.Builder newBuilder(net.corda.p2p.FlowMessageHeader other) {
    if (other == null) {
      return new net.corda.p2p.FlowMessageHeader.Builder();
    } else {
      return new net.corda.p2p.FlowMessageHeader.Builder(other);
    }
  }

  /**
   * RecordBuilder for FlowMessageHeader instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<FlowMessageHeader>
    implements org.apache.avro.data.RecordBuilder<FlowMessageHeader> {

    private net.corda.p2p.HoldingIdentity destination;
    private net.corda.p2p.HoldingIdentity.Builder destinationBuilder;
    private net.corda.p2p.HoldingIdentity source;
    private net.corda.p2p.HoldingIdentity.Builder sourceBuilder;
    private java.lang.Long ttl;
    private java.lang.String messageId;
    private java.lang.String traceId;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.p2p.FlowMessageHeader.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.destination)) {
        this.destination = data().deepCopy(fields()[0].schema(), other.destination);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (other.hasDestinationBuilder()) {
        this.destinationBuilder = net.corda.p2p.HoldingIdentity.newBuilder(other.getDestinationBuilder());
      }
      if (isValidValue(fields()[1], other.source)) {
        this.source = data().deepCopy(fields()[1].schema(), other.source);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
      if (other.hasSourceBuilder()) {
        this.sourceBuilder = net.corda.p2p.HoldingIdentity.newBuilder(other.getSourceBuilder());
      }
      if (isValidValue(fields()[2], other.ttl)) {
        this.ttl = data().deepCopy(fields()[2].schema(), other.ttl);
        fieldSetFlags()[2] = other.fieldSetFlags()[2];
      }
      if (isValidValue(fields()[3], other.messageId)) {
        this.messageId = data().deepCopy(fields()[3].schema(), other.messageId);
        fieldSetFlags()[3] = other.fieldSetFlags()[3];
      }
      if (isValidValue(fields()[4], other.traceId)) {
        this.traceId = data().deepCopy(fields()[4].schema(), other.traceId);
        fieldSetFlags()[4] = other.fieldSetFlags()[4];
      }
    }

    /**
     * Creates a Builder by copying an existing FlowMessageHeader instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.p2p.FlowMessageHeader other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.destination)) {
        this.destination = data().deepCopy(fields()[0].schema(), other.destination);
        fieldSetFlags()[0] = true;
      }
      this.destinationBuilder = null;
      if (isValidValue(fields()[1], other.source)) {
        this.source = data().deepCopy(fields()[1].schema(), other.source);
        fieldSetFlags()[1] = true;
      }
      this.sourceBuilder = null;
      if (isValidValue(fields()[2], other.ttl)) {
        this.ttl = data().deepCopy(fields()[2].schema(), other.ttl);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.messageId)) {
        this.messageId = data().deepCopy(fields()[3].schema(), other.messageId);
        fieldSetFlags()[3] = true;
      }
      if (isValidValue(fields()[4], other.traceId)) {
        this.traceId = data().deepCopy(fields()[4].schema(), other.traceId);
        fieldSetFlags()[4] = true;
      }
    }

    /**
      * Gets the value of the 'destination' field.
      * @return The value.
      */
    public net.corda.p2p.HoldingIdentity getDestination() {
      return destination;
    }


    /**
      * Sets the value of the 'destination' field.
      * @param value The value of 'destination'.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessageHeader.Builder setDestination(net.corda.p2p.HoldingIdentity value) {
      validate(fields()[0], value);
      this.destinationBuilder = null;
      this.destination = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'destination' field has been set.
      * @return True if the 'destination' field has been set, false otherwise.
      */
    public boolean hasDestination() {
      return fieldSetFlags()[0];
    }

    /**
     * Gets the Builder instance for the 'destination' field and creates one if it doesn't exist yet.
     * @return This builder.
     */
    public net.corda.p2p.HoldingIdentity.Builder getDestinationBuilder() {
      if (destinationBuilder == null) {
        if (hasDestination()) {
          setDestinationBuilder(net.corda.p2p.HoldingIdentity.newBuilder(destination));
        } else {
          setDestinationBuilder(net.corda.p2p.HoldingIdentity.newBuilder());
        }
      }
      return destinationBuilder;
    }

    /**
     * Sets the Builder instance for the 'destination' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */

    public net.corda.p2p.FlowMessageHeader.Builder setDestinationBuilder(net.corda.p2p.HoldingIdentity.Builder value) {
      clearDestination();
      destinationBuilder = value;
      return this;
    }

    /**
     * Checks whether the 'destination' field has an active Builder instance
     * @return True if the 'destination' field has an active Builder instance
     */
    public boolean hasDestinationBuilder() {
      return destinationBuilder != null;
    }

    /**
      * Clears the value of the 'destination' field.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessageHeader.Builder clearDestination() {
      destination = null;
      destinationBuilder = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'source' field.
      * @return The value.
      */
    public net.corda.p2p.HoldingIdentity getSource() {
      return source;
    }


    /**
      * Sets the value of the 'source' field.
      * @param value The value of 'source'.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessageHeader.Builder setSource(net.corda.p2p.HoldingIdentity value) {
      validate(fields()[1], value);
      this.sourceBuilder = null;
      this.source = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'source' field has been set.
      * @return True if the 'source' field has been set, false otherwise.
      */
    public boolean hasSource() {
      return fieldSetFlags()[1];
    }

    /**
     * Gets the Builder instance for the 'source' field and creates one if it doesn't exist yet.
     * @return This builder.
     */
    public net.corda.p2p.HoldingIdentity.Builder getSourceBuilder() {
      if (sourceBuilder == null) {
        if (hasSource()) {
          setSourceBuilder(net.corda.p2p.HoldingIdentity.newBuilder(source));
        } else {
          setSourceBuilder(net.corda.p2p.HoldingIdentity.newBuilder());
        }
      }
      return sourceBuilder;
    }

    /**
     * Sets the Builder instance for the 'source' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */

    public net.corda.p2p.FlowMessageHeader.Builder setSourceBuilder(net.corda.p2p.HoldingIdentity.Builder value) {
      clearSource();
      sourceBuilder = value;
      return this;
    }

    /**
     * Checks whether the 'source' field has an active Builder instance
     * @return True if the 'source' field has an active Builder instance
     */
    public boolean hasSourceBuilder() {
      return sourceBuilder != null;
    }

    /**
      * Clears the value of the 'source' field.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessageHeader.Builder clearSource() {
      source = null;
      sourceBuilder = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /**
      * Gets the value of the 'ttl' field.
      * @return The value.
      */
    public java.lang.Long getTtl() {
      return ttl;
    }


    /**
      * Sets the value of the 'ttl' field.
      * @param value The value of 'ttl'.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessageHeader.Builder setTtl(java.lang.Long value) {
      validate(fields()[2], value);
      this.ttl = value;
      fieldSetFlags()[2] = true;
      return this;
    }

    /**
      * Checks whether the 'ttl' field has been set.
      * @return True if the 'ttl' field has been set, false otherwise.
      */
    public boolean hasTtl() {
      return fieldSetFlags()[2];
    }


    /**
      * Clears the value of the 'ttl' field.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessageHeader.Builder clearTtl() {
      ttl = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    /**
      * Gets the value of the 'messageId' field.
      * @return The value.
      */
    public java.lang.String getMessageId() {
      return messageId;
    }


    /**
      * Sets the value of the 'messageId' field.
      * @param value The value of 'messageId'.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessageHeader.Builder setMessageId(java.lang.String value) {
      validate(fields()[3], value);
      this.messageId = value;
      fieldSetFlags()[3] = true;
      return this;
    }

    /**
      * Checks whether the 'messageId' field has been set.
      * @return True if the 'messageId' field has been set, false otherwise.
      */
    public boolean hasMessageId() {
      return fieldSetFlags()[3];
    }


    /**
      * Clears the value of the 'messageId' field.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessageHeader.Builder clearMessageId() {
      messageId = null;
      fieldSetFlags()[3] = false;
      return this;
    }

    /**
      * Gets the value of the 'traceId' field.
      * @return The value.
      */
    public java.lang.String getTraceId() {
      return traceId;
    }


    /**
      * Sets the value of the 'traceId' field.
      * @param value The value of 'traceId'.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessageHeader.Builder setTraceId(java.lang.String value) {
      validate(fields()[4], value);
      this.traceId = value;
      fieldSetFlags()[4] = true;
      return this;
    }

    /**
      * Checks whether the 'traceId' field has been set.
      * @return True if the 'traceId' field has been set, false otherwise.
      */
    public boolean hasTraceId() {
      return fieldSetFlags()[4];
    }


    /**
      * Clears the value of the 'traceId' field.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessageHeader.Builder clearTraceId() {
      traceId = null;
      fieldSetFlags()[4] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public FlowMessageHeader build() {
      try {
        FlowMessageHeader record = new FlowMessageHeader();
        if (destinationBuilder != null) {
          try {
            record.destination = this.destinationBuilder.build();
          } catch (org.apache.avro.AvroMissingFieldException e) {
            e.addParentField(record.getSchema().getField("destination"));
            throw e;
          }
        } else {
          record.destination = fieldSetFlags()[0] ? this.destination : (net.corda.p2p.HoldingIdentity) defaultValue(fields()[0]);
        }
        if (sourceBuilder != null) {
          try {
            record.source = this.sourceBuilder.build();
          } catch (org.apache.avro.AvroMissingFieldException e) {
            e.addParentField(record.getSchema().getField("source"));
            throw e;
          }
        } else {
          record.source = fieldSetFlags()[1] ? this.source : (net.corda.p2p.HoldingIdentity) defaultValue(fields()[1]);
        }
        record.ttl = fieldSetFlags()[2] ? this.ttl : (java.lang.Long) defaultValue(fields()[2]);
        record.messageId = fieldSetFlags()[3] ? this.messageId : (java.lang.String) defaultValue(fields()[3]);
        record.traceId = fieldSetFlags()[4] ? this.traceId : (java.lang.String) defaultValue(fields()[4]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<FlowMessageHeader>
    WRITER$ = (org.apache.avro.io.DatumWriter<FlowMessageHeader>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<FlowMessageHeader>
    READER$ = (org.apache.avro.io.DatumReader<FlowMessageHeader>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    this.destination.customEncode(out);

    this.source.customEncode(out);

    if (this.ttl == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeLong(this.ttl);
    }

    out.writeString(this.messageId);

    out.writeString(this.traceId);

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      if (this.destination == null) {
        this.destination = new net.corda.p2p.HoldingIdentity();
      }
      this.destination.customDecode(in);

      if (this.source == null) {
        this.source = new net.corda.p2p.HoldingIdentity();
      }
      this.source.customDecode(in);

      if (in.readIndex() != 1) {
        in.readNull();
        this.ttl = null;
      } else {
        this.ttl = in.readLong();
      }

      this.messageId = in.readString();

      this.traceId = in.readString();

    } else {
      for (int i = 0; i < 5; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          if (this.destination == null) {
            this.destination = new net.corda.p2p.HoldingIdentity();
          }
          this.destination.customDecode(in);
          break;

        case 1:
          if (this.source == null) {
            this.source = new net.corda.p2p.HoldingIdentity();
          }
          this.source.customDecode(in);
          break;

        case 2:
          if (in.readIndex() != 1) {
            in.readNull();
            this.ttl = null;
          } else {
            this.ttl = in.readLong();
          }
          break;

        case 3:
          this.messageId = in.readString();
          break;

        case 4:
          this.traceId = in.readString();
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










