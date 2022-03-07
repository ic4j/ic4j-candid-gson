/*
 * Copyright 2021 Exilor Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.ic4j.candid.gson;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.ic4j.candid.CandidError;
import org.ic4j.candid.ObjectSerializer;
import org.ic4j.candid.parser.IDLType;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.candid.types.Label;
import org.ic4j.candid.types.Type;
import org.ic4j.types.Principal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class GsonSerializer implements ObjectSerializer {
	Optional<IDLType> idlType = Optional.empty();

	Gson gson = new GsonBuilder().create();

	public static GsonSerializer create(IDLType idlType) {
		GsonSerializer deserializer = new GsonSerializer();
		deserializer.idlType = Optional.ofNullable(idlType);
		return deserializer;

	}

	public static GsonSerializer create() {
		GsonSerializer deserializer = new GsonSerializer();
		return deserializer;
	}

	@Override
	public IDLValue serialize(Object value) {
		if (value == null)
			return IDLValue.create(value);

		if (JsonElement.class.isAssignableFrom(value.getClass()))
			return this.getIDLValue(this.idlType, (JsonElement) value);
		else {
			try {
				// JSON data structure
				JsonElement jsonElement = gson.toJsonTree(value);
				return this.getIDLValue(this.idlType, jsonElement);
			} catch (Exception e) {
				throw CandidError.create(CandidError.CandidErrorCode.CUSTOM, e, e.getLocalizedMessage());
			}
		}
	}

	IDLValue getPrimitiveIDLValue(Type type, JsonPrimitive value) {
		IDLValue result = IDLValue.create(null);

		if (value == null)
			return result;

		switch (type) {
		case BOOL:
			result = IDLValue.create(value.getAsBoolean(), type);
			break;
		case INT:
			result = IDLValue.create(value.getAsBigInteger(), type);
			break;
		case INT8:
			result = IDLValue.create(value.getAsByte(), type);
			break;
		case INT16:
			result = IDLValue.create(value.getAsShort(), type);
			break;
		case INT32:
			result = IDLValue.create(value.getAsInt(), type);
			break;
		case INT64:
			result = IDLValue.create(value.getAsLong(), type);
			break;
		case NAT:
			result = IDLValue.create(value.getAsBigInteger(), type);
			break;
		case NAT8:
			result = IDLValue.create(value.getAsByte(), type);
			break;
		case NAT16:
			result = IDLValue.create(value.getAsShort(), type);
			break;
		case NAT32:
			result = IDLValue.create(value.getAsInt(), type);
			break;
		case NAT64:
			result = IDLValue.create(value.getAsLong(), type);
			break;
		case FLOAT32:
			result = IDLValue.create(value.getAsFloat(), type);
			break;
		case FLOAT64:
			result = IDLValue.create(value.getAsDouble(), type);
			break;
		case TEXT:
			result = IDLValue.create(value.getAsString(), type);
			break;
		case PRINCIPAL:
			result = IDLValue.create(Principal.fromString(value.getAsString()));
			break;
		case EMPTY:
			result = IDLValue.create(null, type);
		case NULL:
			result = IDLValue.create(null, type);
			break;
		}

		return result;
	}

	Type getType(JsonElement value) {
		if (value == null || value.isJsonNull())
			return Type.NULL;

		if (value.isJsonPrimitive()) {
			JsonPrimitive primitiveValue = (JsonPrimitive) value;

			if (primitiveValue.isBoolean())
				return Type.BOOL;
			else if (primitiveValue.isString())
				return Type.TEXT;
			else if (primitiveValue.isNumber()) {
				return IDLType.createType(primitiveValue.getAsNumber()).getType();
			} else
				return Type.NULL;
		} else if (value.isJsonArray())
			return Type.VEC;
		else if (value.isJsonObject())
			return Type.RECORD;
		else
			return Type.NULL;
	}

	IDLValue getIDLValue(Optional<IDLType> expectedIdlType, JsonElement value) {
		// handle null values
		if (value == null)
			return IDLValue.create(value, Type.NULL);

		Type type;
		if (expectedIdlType.isPresent())
			type = expectedIdlType.get().getType();
		else
			type = this.getType(value);
		
		if(type == Type.NULL || type == Type.EMPTY)
			return IDLValue.create(null, type);
			
		// handle primitives
		if (value.isJsonPrimitive())
			return this.getPrimitiveIDLValue(type, (JsonPrimitive) value);

		// handle arrays
		if (type == Type.VEC) {
			IDLType innerIdlType = IDLType.createType(Type.NULL);

			if (expectedIdlType.isPresent())
				innerIdlType = expectedIdlType.get().getInnerType();

			if (innerIdlType != null && (innerIdlType.getType() == Type.INT8 || innerIdlType.getType() == Type.NAT8))
				return IDLValue.create(value, IDLType.createType(type, innerIdlType));

			if (value.isJsonArray()) {
				JsonArray arrayNode = (JsonArray) value;
				Object[] arrayValue = new Object[arrayNode.size()];

				for (int i = 0; i < arrayNode.size(); i++) {
					IDLValue item = this.getIDLValue(Optional.ofNullable(innerIdlType), arrayNode.get(i));

					arrayValue[i] = item.getValue();
					if (!expectedIdlType.isPresent())
						innerIdlType = item.getIDLType();
				}

				IDLType idlType;

				if (expectedIdlType.isPresent())
					idlType = expectedIdlType.get();
				else
					idlType = IDLType.createType(Type.VEC, innerIdlType);

				return IDLValue.create(arrayValue, idlType);
			}

			throw CandidError.create(CandidError.CandidErrorCode.CUSTOM,
					"Cannot convert class " + value.getClass().getName() + " to VEC");

		}

		// handle Objects
		if (type == Type.RECORD || type == Type.VARIANT) {
			Map<Label, Object> valueMap = new TreeMap<Label, Object>();
			Map<Label, IDLType> typeMap = new TreeMap<Label, IDLType>();
			Map<Label, IDLType> expectedTypeMap = new TreeMap<Label, IDLType>();
			
			if (expectedIdlType.isPresent())
				expectedTypeMap = expectedIdlType.get().getTypeMap();
			
			if(value.isJsonArray())
			{
				JsonArray arrayNode = (JsonArray) value;
				for (int i = 0; i < arrayNode.size(); i++) {
					JsonElement item = arrayNode.get(i);
					IDLType expectedItemIdlType;
					
					if (expectedTypeMap != null && expectedIdlType.isPresent())
						expectedItemIdlType = expectedTypeMap.get(Label.createUnnamedLabel((long)i));
					else
						expectedItemIdlType = IDLType.createType(this.getType(item));
	
					if (expectedItemIdlType == null)
						continue;
	
					IDLValue itemIdlValue = this.getIDLValue(Optional.ofNullable(expectedItemIdlType), item);
	
					typeMap.put(Label.createUnnamedLabel((long)i), itemIdlValue.getIDLType());
					valueMap.put(Label.createUnnamedLabel((long)i), itemIdlValue.getValue());
				}								
			}
			else
			{
				JsonObject objectNode = (JsonObject) value;
	
				Iterator<String> fieldNames = objectNode.keySet().iterator();
	
				while (fieldNames.hasNext()) {
					String name = fieldNames.next();
	
					JsonElement item = objectNode.get(name);
	
					IDLType expectedItemIdlType;
	
					if (expectedTypeMap != null && expectedIdlType.isPresent())
						expectedItemIdlType = expectedTypeMap.get(Label.createNamedLabel(name));
					else
						expectedItemIdlType = IDLType.createType(this.getType(item));
	
					if (expectedItemIdlType == null)
						continue;
	
					IDLValue itemIdlValue = this.getIDLValue(Optional.ofNullable(expectedItemIdlType), item);
	
					typeMap.put(Label.createNamedLabel((String) name), itemIdlValue.getIDLType());
					valueMap.put(Label.createNamedLabel((String) name), itemIdlValue.getValue());
				}
			}

			IDLType idlType = IDLType.createType(type, typeMap);
			IDLValue idlValue = IDLValue.create(valueMap, idlType);

			return idlValue;
		}
		
		if (type == Type.OPT)
		{
			if (expectedIdlType.isPresent())
			{
				if(value.isJsonArray() && value.getAsJsonArray().isEmpty())
					return IDLValue.create(Optional.empty(), expectedIdlType.get());
				
				IDLValue itemIdlValue = this.getIDLValue(Optional.ofNullable(expectedIdlType.get().getInnerType()), value);
				
				return IDLValue.create(Optional.ofNullable(itemIdlValue.getValue()), expectedIdlType.get());
			}
			else
			{
				if(value.isJsonNull())
					return IDLValue.create(Optional.empty(), IDLType.createType(Type.OPT));
				
				if(value.isJsonArray() && value.getAsJsonArray().isEmpty())
					return IDLValue.create(Optional.empty(), IDLType.createType(Type.OPT));
				
				IDLValue itemIdlValue = this.getIDLValue(Optional.ofNullable( IDLType.createType(Type.OPT)), value);
				
				
				return IDLValue.create(Optional.ofNullable(itemIdlValue.getValue()), IDLType.createType(Type.OPT));							
			}							
		}

		throw CandidError.create(CandidError.CandidErrorCode.CUSTOM, "Cannot convert type " + type.name());

	}
}
