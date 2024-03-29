## JSON (Gson) serialization and deserialization

Use GsonSerializer to serialize Gson JsonElement or Gson compatible Pojo class to Candid. Full documentation <a href=" https://docs.ic4j.com/reference/api-reference/object-serializers-and-deserializers/json-gson-serializer-and-deserializer">
here</a>

```
JsonElement jsonValue;
IDLType idlType;

IDLValue idlValue = IDLValue.create(jsonValue, GsonSerializer.create(idlType));
List<IDLValue> args = new ArrayList<IDLValue>();
args.add(idlValue);

IDLArgs idlArgs = IDLArgs.create(args);

byte[] buf = idlArgs.toBytes();
```

Use GsonDeserializer to deserialize Candid to Gson JsonElement or Gson compatible Pojo class

```
JsonElement jsonResult = IDLArgs.fromBytes(buf).getArgs().get(0)
	.getValue(GsonDeserializer.create(idlValue.getIDLType()), JsonElement.class);
```

# Downloads / Accessing Binaries

To add Java IC4J Candid library to your Java project use Maven or Gradle import from Maven Central.

<a href="https://search.maven.org/artifact/ic4j/ic4j-candid-gson/0.6.19.7/jar">
https://search.maven.org/artifact/ic4j/ic4j-candid-gson/0.6.19.7/jar
</a>

```
<dependency>
  <groupId>org.ic4j</groupId>
  <artifactId>ic4j-candid-gson</artifactId>
  <version>0.6.19.7</version>
</dependency>
```

```
implementation 'org.ic4j:ic4j-candid-gson:0.6.19.7'
```

## Dependencies

This this is using these open source libraries


### Gson JSON Serializer and Deserializer
To manage Gson objects.

# Build

You need JDK 8+ to build IC4J Candid Gson.
