package org.ic4j.candid.gson.test;

import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.ic4j.candid.gson.GsonDeserializer;
import org.ic4j.candid.gson.GsonSerializer;
import org.ic4j.candid.parser.IDLArgs;
import org.ic4j.candid.parser.IDLType;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.candid.types.Label;
import org.ic4j.candid.types.Type;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

public final class GsonTest {
	static Logger LOG;

	static final String SIMPLE_NODE_FILE = "SimpleNode.json";
	static final String SIMPLE_ARRAY_NODE_FILE = "SimpleArrayNode.json";

	Gson gson = new Gson();

	static {
		LOG = LoggerFactory.getLogger(GsonTest.class);
	}

	@Test
	public void test() {

		Map<Label, IDLType> typeMap = new TreeMap<Label, IDLType>();

		typeMap.put(Label.createNamedLabel("bar"), IDLType.createType(Type.BOOL));
		typeMap.put(Label.createNamedLabel("foo"), IDLType.createType(Type.INT));

		this.testJson(SIMPLE_NODE_FILE, IDLType.createType(Type.RECORD, typeMap));

		IDLType idlType = IDLType.createType(Type.VEC, IDLType.createType(Type.RECORD, typeMap));

		this.testJson(SIMPLE_ARRAY_NODE_FILE, idlType);

		GsonPojo pojo = new GsonPojo();

		pojo.bar = true;
		pojo.foo = BigInteger.valueOf(42);

		IDLValue idlValue = IDLValue.create(pojo, GsonSerializer.create());
		List<IDLValue> args = new ArrayList<IDLValue>();
		args.add(idlValue);

		IDLArgs idlArgs = IDLArgs.create(args);

		byte[] buf = idlArgs.toBytes();

		GsonPojo pojoResult = IDLArgs.fromBytes(buf).getArgs().get(0)
				.getValue(GsonDeserializer.create(idlValue.getIDLType()), GsonPojo.class);

		Assertions.assertEquals(pojo, pojoResult);
	}

	void testJson(String fileName, IDLType idlType) {
		try {
			JsonElement jsonValue = readNode(fileName);

			IDLValue idlValue = IDLValue.create(jsonValue, GsonSerializer.create(idlType));
			List<IDLValue> args = new ArrayList<IDLValue>();
			args.add(idlValue);

			IDLArgs idlArgs = IDLArgs.create(args);

			byte[] buf = idlArgs.toBytes();

			JsonElement jsonResult = IDLArgs.fromBytes(buf).getArgs().get(0)
					.getValue(GsonDeserializer.create(idlValue.getIDLType()), JsonElement.class);

			JSONAssert.assertEquals(jsonValue.toString(), jsonResult.toString(), JSONCompareMode.LENIENT);

		} catch (IOException e) {
			LOG.error(e.getLocalizedMessage(), e);
			Assertions.fail(e.getMessage());
		} catch (JSONException e) {
			LOG.error(e.getLocalizedMessage(), e);
			Assertions.fail(e.getMessage());
		}
	}

	JsonElement readNode(String fileName) throws IOException {
		Reader reader = Files.newBufferedReader(Paths.get(getClass().getClassLoader().getResource(fileName).getPath()));

		JsonElement rootNode = gson.fromJson(reader, JsonElement.class);
		return rootNode;
	}
}
