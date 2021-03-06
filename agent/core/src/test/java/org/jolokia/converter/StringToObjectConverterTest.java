package org.jolokia.converter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;


/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


/**
 * @author roland
 * @since Feb 14, 2010
 */
public class StringToObjectConverterTest {

    StringToObjectConverter converter;

    @BeforeTest
    public void setup() {
       converter = new StringToObjectConverter();
    }

    @Test
    public void simpleConversions() {
        Object obj = converter.convertFromString(int.class.getCanonicalName(),"10");
        assertEquals("Int conversion",10,obj);
        obj = converter.convertFromString(Integer.class.getCanonicalName(),"10");
        assertEquals("Integer conversion",10,obj);
        obj = converter.convertFromString(Short.class.getCanonicalName(),"10");
        assertEquals("Short conversion",(short) 10,obj);
        obj = converter.convertFromString(short.class.getCanonicalName(),"10");
        assertEquals("short conversion",Short.parseShort("10"),obj);
        obj = converter.convertFromString(Long.class.getCanonicalName(),"10");
        assertEquals("long conversion",10L,obj);
        obj = converter.convertFromString(long.class.getCanonicalName(),"10");
        assertEquals("Long conversion",10L,obj);
        obj = converter.convertFromString(Byte.class.getCanonicalName(),"10");
        assertEquals("Byte conversion",(byte) 10,obj);
        obj = converter.convertFromString(byte.class.getCanonicalName(),"10");
        assertEquals("byte conversion",Byte.parseByte("10"),obj);

        obj = converter.convertFromString(Float.class.getCanonicalName(),"10.5");
        assertEquals("Float conversion",10.5f,obj);
        obj = converter.convertFromString(float.class.getCanonicalName(),"21.3");
        assertEquals("float conversion",new Float(21.3f),obj);
        obj = converter.convertFromString(Double.class.getCanonicalName(),"10.5");
        assertEquals("Double conversion",10.5d,obj);
        obj = converter.convertFromString(double.class.getCanonicalName(),"21.3");
        assertEquals("double conversion",21.3d,obj);

        obj = converter.convertFromString(Boolean.class.getCanonicalName(),"false");
        assertEquals("Boolean conversion",false,obj);
        obj = converter.convertFromString(boolean.class.getCanonicalName(),"true");
        assertEquals("boolean conversion",true,obj);

        obj = converter.convertFromString(char.class.getCanonicalName(),"a");
        assertEquals("Char conversion",'a',obj);

        obj = converter.convertFromString("java.lang.String","10");
        assertEquals("String conversion","10",obj);
    }

    @Test
    public void jsonConversion() {
        JSONObject json = new JSONObject();
        json.put("name","roland");
        json.put("kind","jolokia");

        Object object = converter.convertFromString(JSONObject.class.getName(),json.toString());
        assertEquals(json,object);

        JSONArray array = new JSONArray();
        array.add("roland");
        array.add("jolokia");

        object = converter.convertFromString(JSONArray.class.getName(),array.toString());
        assertEquals(array,object);
    }

    @Test
    public void arrayConversions() {
        Object obj = converter.convertFromString(new int[0].getClass().getName(),"10,20,30");
        int expected[] = new int[] { 10,20,30};
        for (int i = 0;i < expected.length;i++) {
            assertEquals(expected[i],((int[]) obj)[i]);
        }
        obj = converter.convertFromString(new Integer[0].getClass().getName(),"10,20,30");
        for (int i = 0;i < expected.length;i++) {
            assertEquals(expected[i],(int) ((Integer[]) obj)[i]);
        }

        try {
            obj = converter.convertFromString("[Lbla;","10,20,30");
            fail("Unknown object type");
        } catch (IllegalArgumentException exp) {}


        try {
            obj = converter.convertFromString("[X","10,20,30");
            fail("Unknown object type");
        } catch (IllegalArgumentException exp) {}
    }

    @Test
    public void checkNull() {
        Object obj = converter.convertFromString(new int[0].getClass().getName(),"[null]");
        assertNull("Null check",obj);
    }

    @Test
    public void checkEmptyString() {
        Object obj = converter.convertFromString("java.lang.String","\"\"");
        assertEquals("Empty String check",0,((String) obj).length());
        try {
            obj = converter.convertFromString("java.lang.Integer","\"\"");
            fail("Empty string conversion only for string");
        } catch (IllegalArgumentException exp) {}
    }

    @Test
    public void unknownExtractor() {
        try {
            Object obj = converter.convertFromString(this.getClass().getName(),"bla");
            fail("Unknown extractor");
        } catch (IllegalArgumentException exp) {};
    }
}
