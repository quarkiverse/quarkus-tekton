package io.quarkiverse.tekton.common.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.tekton.v1.Param;

class ParamsTest {

    @Test
    void testCreateWithStringValue() {
        Param param = Params.create("myKey", "myValue");
        assertNotNull(param, "Param should not be null");
        assertEquals("myKey", param.getName(), "Param name should match the input key");
        assertNotNull(param.getValue(), "Param value should not be null");
        assertEquals("myValue", param.getValue().getStringVal(), "String value should match the input value");
        assertTrue(param.getValue().getArrayVal().isEmpty(), "ArrayVal should be empty for a String input");
        assertNull(param.getValue().getObjectVal(), "ObjectVal should be null for a String input");
    }

    @Test
    void testCreateWithOddListValue() {
        Map<String, String> paramsMap = new LinkedHashMap<>();
        paramsMap.put("myKey", "myValue");

        List<Param> param = Params.create(paramsMap);
        assertNotNull(param, "Param should not be null");
        assertFalse(param.isEmpty(), "Param should not be empty");
        Param p0 = param.get(0);
        assertEquals("myKey", p0.getName());
        assertEquals("myValue", p0.getValue().getStringVal());

        paramsMap = new LinkedHashMap<>();
        paramsMap.put("myKey", "myValue");
        paramsMap.put("myKey2", "myValue2");
        param = Params.create(paramsMap);
        assertNotNull(param, "Param should not be null");
        assertFalse(param.isEmpty(), "Param should not be empty");
        p0 = param.get(0);
        assertEquals("myKey", p0.getName());
        assertEquals("myValue", p0.getValue().getStringVal());

        Param p1 = param.get(1);
        assertEquals("myKey2", p1.getName());
        assertEquals("myValue2", p1.getValue().getStringVal());
    }

    @Test
    void testCreateWithEvenListValue() {
        Map<String, String> paramsMap = new LinkedHashMap<>();
        paramsMap.put("myKey", "myValue");
        paramsMap.put("myOtherKey", "myOtherValue");
        paramsMap.put("thridKey", "thirdValue");
        paramsMap.put("forthKey", "forthValue");

        List<Param> param = Params.create(paramsMap);
        assertNotNull(param, "Param should not be null");
        assertFalse(param.isEmpty(), "Param should not be empty");
        Param p0 = param.get(0);
        assertEquals("myKey", p0.getName());
        assertEquals("myValue", p0.getValue().getStringVal());
        Param p1 = param.get(1);
        assertEquals("myOtherKey", p1.getName());
        assertEquals("myOtherValue", p1.getValue().getStringVal());
        Param p2 = param.get(2);
        assertEquals("thridKey", p2.getName());
        assertEquals("thirdValue", p2.getValue().getStringVal());
        Param p3 = param.get(3);
        assertEquals("forthKey", p3.getName());
        assertEquals("forthValue", p3.getValue().getStringVal());
    }

    @Test
    void testCreateWithArrayValue() {
        String[] arrayVal = { "val1", "val2" };
        Param param = Params.create("myArrayKey", arrayVal);

        assertNotNull(param, "Param should not be null");
        assertEquals("myArrayKey", param.getName(), "Param name should match the input key");
        assertNotNull(param.getValue(), "Param value should not be null");
        assertNotNull(param.getValue().getArrayVal(), "ArrayVal should not be null for an array input");
        for (int i = 0; i < arrayVal.length; i++) {
            assertEquals(param.getValue().getArrayVal().get(i), arrayVal[i], "ArrayVal should match the input array");
        }
        assertNull(param.getValue().getStringVal(), "StringVal should be null for an array input");
        assertNull(param.getValue().getObjectVal(), "ObjectVal should be null for an array input");
    }

    @Test
    void testCreateWithMapValue() {
        Map<String, String> mapVal = new HashMap<>();
        mapVal.put("k1", "v1");
        mapVal.put("k2", "v2");

        Param param = Params.create("myMapKey", mapVal);

        assertNotNull(param, "Param should not be null");
        assertEquals("myMapKey", param.getName(), "Param name should match the input key");
        assertNotNull(param.getValue(), "Param value should not be null");
        assertEquals(mapVal, param.getValue().getObjectVal(), "ObjectVal should match the input map");
        assertNull(param.getValue().getStringVal(), "StringVal should be null for a map input");
        assertTrue(param.getValue().getArrayVal().isEmpty(), "ArrayVal should be empty for a map input");
    }

    @Test
    void testCreateFromSingleStringWithEquals() {
        Param param = Params.create("myKey=myValue");

        assertNotNull(param, "Param should not be null");
        assertEquals("myKey", param.getName(), "Key should be parsed from the string before '='");
        assertNotNull(param.getValue(), "Param value should not be null");
        assertEquals("myValue", param.getValue().getStringVal(), "Value should be parsed from the string after '='");
    }

    @Test
    void testCreateFromSingleStringWithMultipleDashes() {
        Param param = Params.create("---anotherKey=someValue");

        assertNotNull(param, "Param should not be null");
        assertEquals("anotherKey", param.getName(), "Key should remove leading dashes");
        assertEquals("someValue", param.getValue().getStringVal(), "Value should be parsed correctly after '='");
    }

    @Test
    void testCreateFromSingleStringWithSpace() {
        String input = "myKey myValue";
        Param param = Params.create(input);

        assertNotNull(param);
        assertEquals("myKey", param.getName());
        assertEquals("myValue", param.getValue().getStringVal());
    }
}
