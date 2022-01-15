## JSON (Gson) serialization and deserialization

Use JacksonSerializer to serialize Gson JsonElement or Gson compatible Pojo class to Candid

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
