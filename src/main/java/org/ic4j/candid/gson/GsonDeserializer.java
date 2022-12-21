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

import java.math.BigInteger;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.ic4j.candid.CandidError;
import org.ic4j.candid.ObjectDeserializer;
import org.ic4j.candid.jackson.JacksonSerializer;
import org.ic4j.candid.parser.IDLType;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.candid.types.Label;
import org.ic4j.candid.types.Type;
import org.ic4j.types.Principal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class GsonDeserializer implements ObjectDeserializer {
	Optional<IDLType> idlType = Optional.empty();
	Gson gson = new GsonBuilder().create();

	public static GsonDeserializer create(IDLType idlType) {
		GsonDeserializer deserializer = new GsonDeserializer();
		deserializer.idlType = Optional.ofNullable(idlType);
		return deserializer;

	}

	public static GsonDeserializer create() {
		GsonDeserializer deserializer = new GsonDeserializer();
		return deserializer;
	}
	
	public void setIDLType(IDLType idlType)
	{
		this.idlType = Optional.ofNullable(idlType);
	}
	
	public Class<?> getDefaultResponseClass() {
		return JsonElement.class;
	}	

	@Override
	public <T> T deserialize(IDLValue value, Class<T> clazz) {
		if (clazz != null) {
			
			if (JsonElement.class.isAssignableFrom(clazz))
			{
				JsonElement jsonElement = this.getValue(value.getIDLType(), this.idlType, value.getValue());
				return (T) jsonElement;
			}
			else {
				if(!this.idlType.isPresent())
					this.idlType = Optional.ofNullable(GsonSerializer.getIDLType(clazz));
				
				JsonElement jsonElement = this.getValue(value.getIDLType(), this.idlType, value.getValue());
				return (T) gson.fromJson(jsonElement, clazz);
			}
		} else
			throw CandidError.create(CandidError.CandidErrorCode.CUSTOM, "Class is not defined");
	}

	JsonElement getPrimitiveValue(Type type, Object value) {
		JsonElement result = JsonNull.INSTANCE;

		if (value == null)
			return result;

		switch (type) {
		case BOOL:
			result = new JsonPrimitive((Boolean) value);
			break;
		case INT:
			result = new JsonPrimitive((BigInteger) value);
			break;
		case INT8:
			result = new JsonPrimitive((Byte) value);
			break;
		case INT16:
			result = new JsonPrimitive((Short) value);
			break;
		case INT32:
			result = new JsonPrimitive((Integer) value);
			break;
		case INT64:
			result = new JsonPrimitive((Long) value);
		case NAT:
			result = new JsonPrimitive((BigInteger) value);
			break;
		case NAT8:
			result = new JsonPrimitive((Byte) value);
			break;
		case NAT16:
			result = new JsonPrimitive((Short) value);
			break;
		case NAT32:
			result = new JsonPrimitive((Integer) value);
			break;
		case NAT64:
			result = new JsonPrimitive((Long) value);
			break;
		case FLOAT32:
			result = new JsonPrimitive((Float) value);
			break;
		case FLOAT64:
			result = new JsonPrimitive((Double) value);
			break;
		case TEXT:
			result = new JsonPrimitive((String) value);
			break;
		case EMPTY:
			result = new JsonObject();
			break;
		case PRINCIPAL:
			Principal principal = (Principal) value;
			result = new JsonPrimitive(principal.toString());
			break;
		}

		return result;
	}

	JsonElement getValue(IDLType idlType, Optional<IDLType> expectedIdlType, Object value) {
		JsonElement result = JsonNull.INSTANCE;

		if (value == null)
			return result;

		Type type = Type.NULL;

		if (expectedIdlType.isPresent()) {
			type = expectedIdlType.get().getType();
			if (idlType != null)
				idlType = expectedIdlType.get();
		}
		else if(idlType != null)
			type = idlType.getType();

		if (type.isPrimitive())
			return this.getPrimitiveValue(type, value);

		// handle VEC
		if (type == Type.VEC) {
			IDLType expectedInnerIDLType = null;
			IDLType innerIdlType = idlType.getInnerType();

			if (expectedIdlType.isPresent()) {
				expectedInnerIDLType = expectedIdlType.get().getInnerType();
				innerIdlType = expectedInnerIDLType;
			}

			// handle byte array
			if (innerIdlType.getType() == Type.INT8 || innerIdlType.getType() == Type.NAT8)
				return new JsonPrimitive(Base64.getEncoder().encodeToString((byte[]) value));
			else {
				JsonArray arrayNode = new JsonArray();

				Object[] arrayValue = (Object[]) value;

				for (Object item : arrayValue)
					arrayNode.add(
							this.getValue(idlType.getInnerType(), Optional.ofNullable(expectedInnerIDLType), item));

				return arrayNode;
			}
		}

		// handle OPT
		if (type == Type.OPT) {
			Optional optionalValue = (Optional) value;

			if (optionalValue.isPresent()) {
				IDLType expectedInnerIDLType = null;

				if (expectedIdlType.isPresent())
					expectedInnerIDLType = expectedIdlType.get().getInnerType();

				return this.getValue(idlType.getInnerType(), Optional.ofNullable(expectedInnerIDLType),
						optionalValue.get());
			} else
				return result;
		}

		if (type == Type.RECORD || type == Type.VARIANT) {
			JsonArray arrayNode = new JsonArray();
			JsonObject treeNode = new JsonObject();

			Map<Label, Object> valueMap = (Map<Label, Object>) value;

			Map<Label, IDLType> typeMap = idlType.getTypeMap();

			Map<Label, IDLType> expectedTypeMap = new TreeMap<Label, IDLType>();

			if (expectedIdlType.isPresent() && expectedIdlType.get().getTypeMap() != null)
				expectedTypeMap = expectedIdlType.get().getTypeMap();

			Set<Label> labels = valueMap.keySet();

			Map<Long, Label> expectedLabels = new TreeMap<Long, Label>();

			for (Label entry : expectedTypeMap.keySet())
				expectedLabels.put(entry.getId(), entry);

			for (Label label : labels) {
				boolean isNamed = false;
				
				if(label.getType() == Label.LabelType.NAMED)
					isNamed = true;
				
				String fieldName;

				IDLType itemIdlType = typeMap.get(label);

				IDLType expectedItemIdlType = null;

				if (expectedTypeMap.containsKey(label)) {
					expectedItemIdlType = expectedTypeMap.get(label);

					Label expectedLabel = expectedLabels.get(label.getId());
					
					if(expectedLabel.getType() == Label.LabelType.NAMED)
						isNamed = true;

					fieldName = expectedLabel.getValue().toString();
				} else
					fieldName = label.getValue().toString();

				JsonElement itemNode = this.getValue(itemIdlType, Optional.ofNullable(expectedItemIdlType),
						valueMap.get(label));

				if(isNamed)
					treeNode.add(fieldName, itemNode);
				else
					arrayNode.add(itemNode);
			}

			if(arrayNode.isEmpty())
				return treeNode;
			else if(treeNode.size() == 0)
				return arrayNode;
			else
			{
				arrayNode.add(treeNode);
				return arrayNode;
			}
		}
		throw CandidError.create(CandidError.CandidErrorCode.CUSTOM, "Cannot convert type " + type.name());
	}

}
