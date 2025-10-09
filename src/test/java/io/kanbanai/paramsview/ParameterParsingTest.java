package io.kanbanai.paramsview;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;
import java.lang.reflect.Method;

/**
 * Test class for parameter parsing logic
 * 
 * Tests the parseParameterValues method to ensure it correctly handles:
 * - Single values
 * - Array values with brackets [value1,value2]
 * - Mixed single and array values
 */
public class ParameterParsingTest {
    
    /**
     * Helper method to invoke private parseParameterValues method using reflection
     */
    private Map<String, String> parseParameterValues(Object instance, String paramsStr) throws Exception {
        Method method = instance.getClass().getDeclaredMethod("parseParameterValues", String.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(instance, paramsStr);
    }
    
    @Test
    public void testSingleValueParsing() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test simple single value
        Map<String, String> result = parseParameterValues(api, "Channel:C01");
        
        assertEquals("Should have 1 parameter", 1, result.size());
        assertEquals("Channel value should be C01", "C01", result.get("Channel"));
    }
    
    @Test
    public void testMultipleSingleValues() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test multiple single values
        Map<String, String> result = parseParameterValues(api, "Channel:C01,Environment:DEV,Version:1.0.0");
        
        assertEquals("Should have 3 parameters", 3, result.size());
        assertEquals("Channel value should be C01", "C01", result.get("Channel"));
        assertEquals("Environment value should be DEV", "DEV", result.get("Environment"));
        assertEquals("Version value should be 1.0.0", "1.0.0", result.get("Version"));
    }
    
    @Test
    public void testArrayValueParsing() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test array value with brackets
        Map<String, String> result = parseParameterValues(api, "depen:[OptionB,OptionA]");
        
        assertEquals("Should have 1 parameter", 1, result.size());
        assertEquals("depen value should be comma-separated", "OptionB,OptionA", result.get("depen"));
    }
    
    @Test
    public void testMixedSingleAndArrayValues() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test mixed single and array values (actual use case)
        Map<String, String> result = parseParameterValues(api, "Channel:C01,depen:[OptionB,OptionA]");
        
        assertEquals("Should have 2 parameters", 2, result.size());
        assertEquals("Channel value should be C01", "C01", result.get("Channel"));
        assertEquals("depen value should be comma-separated", "OptionB,OptionA", result.get("depen"));
    }
    
    @Test
    public void testComplexArrayValues() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test multiple array values
        Map<String, String> result = parseParameterValues(api, "param1:[A,B,C],param2:[X,Y,Z],single:value");
        
        assertEquals("Should have 3 parameters", 3, result.size());
        assertEquals("param1 should have 3 values", "A,B,C", result.get("param1"));
        assertEquals("param2 should have 3 values", "X,Y,Z", result.get("param2"));
        assertEquals("single value should be parsed", "value", result.get("single"));
    }
    
    @Test
    public void testArrayWithSpaces() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test array values with spaces
        Map<String, String> result = parseParameterValues(api, "options:[Option A,Option B,Option C]");
        
        assertEquals("Should have 1 parameter", 1, result.size());
        assertEquals("options should preserve spaces", "Option A,Option B,Option C", result.get("options"));
    }
    
    @Test
    public void testEmptyArray() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test empty array
        Map<String, String> result = parseParameterValues(api, "empty:[]");
        
        assertEquals("Should have 1 parameter", 1, result.size());
        assertEquals("empty array should be empty string", "", result.get("empty"));
    }
    
    @Test
    public void testSingleValueInArray() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test single value in array brackets
        Map<String, String> result = parseParameterValues(api, "single:[OnlyOne]");
        
        assertEquals("Should have 1 parameter", 1, result.size());
        assertEquals("single array value should work", "OnlyOne", result.get("single"));
    }
    
    @Test
    public void testMalformedArrayMissingCloseBracket() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test malformed array (missing close bracket)
        Map<String, String> result = parseParameterValues(api, "bad:[value1,value2");
        
        assertEquals("Should handle malformed array", 1, result.size());
        // Should treat as regular string since bracket is not closed
        assertTrue("Should contain the value", result.containsKey("bad"));
    }
    
    @Test
    public void testEmptyString() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test empty string
        Map<String, String> result = parseParameterValues(api, "");
        
        assertEquals("Empty string should return empty map", 0, result.size());
    }
    
    @Test
    public void testNullString() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test null string
        Map<String, String> result = parseParameterValues(api, null);
        
        assertEquals("Null string should return empty map", 0, result.size());
    }
    
    @Test
    public void testValuesWithColons() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test values containing colons (e.g., URLs or time formats)
        Map<String, String> result = parseParameterValues(api, "url:https://example.com,time:10:30:00");
        
        assertEquals("Should have 2 parameters", 2, result.size());
        assertEquals("url should preserve colons", "https://example.com", result.get("url"));
        assertEquals("time should preserve colons", "10:30:00", result.get("time"));
    }
    
    @Test
    public void testRealWorldScenario() throws Exception {
        RenderedParametersApi api = new RenderedParametersApi();
        
        // Test real-world scenario from the request
        String input = "Channel:C01,depen:[OptionB,OptionA]";
        Map<String, String> result = parseParameterValues(api, input);
        
        assertEquals("Should parse both parameters", 2, result.size());
        assertEquals("Channel should be C01", "C01", result.get("Channel"));
        assertEquals("depen should contain both options", "OptionB,OptionA", result.get("depen"));
        
        // Verify that the array values are comma-separated for easy splitting
        String[] depenValues = result.get("depen").split(",");
        assertEquals("depen should have 2 values", 2, depenValues.length);
        assertEquals("First value should be OptionB", "OptionB", depenValues[0]);
        assertEquals("Second value should be OptionA", "OptionA", depenValues[1]);
    }
}
