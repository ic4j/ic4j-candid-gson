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

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
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
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

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
	
	public void setIDLType(IDLType idlType)
	{
		this.idlType = Optional.ofNullable(idlType);
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
	
	public static IDLType getIDLType(Class valueClass)
	{
		// handle null values
		if(valueClass == null)
			return IDLType.createType(Type.NULL);
		
		if(IDLType.isDefaultType(valueClass))
			return IDLType.createType(valueClass);		
		
		if(Optional.class.isAssignableFrom(valueClass))
			return IDLType.createType(Type.OPT);
		
		if(List.class.isAssignableFrom(valueClass))
			return IDLType.createType(Type.VEC);		
		
		if(GregorianCalendar.class.isAssignableFrom(valueClass))
			return IDLType.createType(Type.INT);
		
		if(Date.class.isAssignableFrom(valueClass))
			return IDLType.createType(Type.INT);		

		Map<Label,IDLType> typeMap = new TreeMap<Label,IDLType>();

		Field[] fields = valueClass.getDeclaredFields();		
		
		for(Field field : fields)
		{
			if(field.isAnnotationPresent(Expose.class))
			{	
				Expose expose = field.getAnnotation(Expose.class);
				
				if(!expose.deserialize())
					continue;
			}
			
			if(field.isEnumConstant())
				continue;			
			
			String name = field.getName();
			if(name.startsWith("this$"))
				continue;
			
			if(name.startsWith("$VALUES"))
				continue;			
			
			if(name.startsWith("ENUM$VALUES"))
				continue;			
			
			Class typeClass = field.getType();	
			
			IDLType fieldType = getIDLType(typeClass);
			
			if(field.isAnnotationPresent(SerializedName.class))
				name = field.getAnnotation(SerializedName.class).value();
			
			Label label = Label.createNamedLabel((String)name);			
						
			boolean isArray = typeClass.isArray();
			boolean isOptional = Optional.class.isAssignableFrom(typeClass);
			
			if(IDLType.isDefaultType(typeClass) || GregorianCalendar.class.isAssignableFrom(typeClass) || Date.class.isAssignableFrom(typeClass))
			{
				// if we do not specify type in annotation and type is one of default
				typeMap.put(label, fieldType);	
				continue;
			}
			else if(List.class.isAssignableFrom(typeClass)) 
			{	
				isArray = true;
				typeClass = (Class)((ParameterizedType)field.getGenericType()).getActualTypeArguments()[0];
				
				fieldType = getIDLType(typeClass);
			}			
			
			// do nested type introspection if type is RECORD		
			if(fieldType.getType() == Type.RECORD || fieldType.getType() == Type.VARIANT)
			{
				String className = typeClass.getSimpleName();
				
				// handle RECORD arrays
				if(isArray)
				{
					fieldType.setName(className);
					fieldType = IDLType.createType(Type.VEC, fieldType);
				}
				else
					fieldType.setName(className);

			}else if(isArray)
			{
				// handle arrays , not record types
				fieldType = IDLType.createType(Type.VEC, fieldType);
			}else if(isOptional)
			{
				// handle Optional, not record types
				
				fieldType = IDLType.createType(Type.OPT, fieldType);
			}
			
			typeMap.put(label, fieldType);	

		}	
		
		IDLType idlType;
		
		if(valueClass.isEnum()) 
		{
			Class<Enum> enumClass = (Class<Enum>)valueClass;
			Enum[] constants = enumClass.getEnumConstants();
			
			for (Enum constant : constants) {
				String name = constant.name();
				
				try {
					if (enumClass.getField(name).isAnnotationPresent(SerializedName.class))
						name = enumClass.getField(name).getAnnotation(SerializedName.class).value();					
				} catch (NoSuchFieldException | SecurityException e) {
					continue;
				}
				
				Label namedLabel = Label.createNamedLabel(name);

				if (!typeMap.containsKey(namedLabel))
					typeMap.put(namedLabel, null);
			}			
			idlType = IDLType.createType(Type.VARIANT, typeMap);
		}
		else
			idlType = IDLType.createType(Type.RECORD, typeMap);
		
		idlType.setName(valueClass.getSimpleName());
		
		return idlType;		
	}
}
