package io.kanbanai.amtIntegration;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;

import io.kanbanai.amtIntegration.util.ParameterParsingUtils;

/**
 * Test class for parameter parsing logic
 * 
 * Tests the parseParameterValues method to ensure it correctly handles:
 * - Single values
 * - Array values with brackets [value1,value2]
 * - Mixed single and array values
 */
public class ParameterParsingTest {
    
    @Test
    public void testSingleValueParsing() throws Exception {
        // Test simple single value
        Map<String, String> result = ParameterParsingUtils.parseParameterValues("Channel:C01");

        assertEquals("Should have 1 parameter", 1, result.size());
        assertEquals("Channel value should be C01", "C01", result.get("Channel"));
    }
    
    @Test
    public void testMultipleSingleValues() throws Exception {
        // Test multiple single values
        Map<String, String> result = ParameterParsingUtils.parseParameterValues("Channel:C01,Environment:DEV,Version:1.0.0");

        assertEquals("Should have 3 parameters", 3, result.size());
        assertEquals("Channel value should be C01", "C01", result.get("Channel"));
        assertEquals("Environment value should be DEV", "DEV", result.get("Environment"));
        assertEquals("Version value should be 1.0.0", "1.0.0", result.get("Version"));
    }

    @Test
    public void testArrayValueParsing() throws Exception {
        // Test array value with brackets
        Map<String, String> result = ParameterParsingUtils.parseParameterValues("depen:[OptionB,OptionA]");

        assertEquals("Should have 1 parameter", 1, result.size());
        assertEquals("depen value should be comma-separated", "OptionB,OptionA", result.get("depen"));
    }

    @Test
    public void testMixedSingleAndArrayValues() throws Exception {
        // Test mixed single and array values (actual use case)
        Map<String, String> result = ParameterParsingUtils.parseParameterValues("Channel:C01,depen:[OptionB,OptionA]");

        assertEquals("Should have 2 parameters", 2, result.size());
        assertEquals("Channel value should be C01", "C01", result.get("Channel"));
        assertEquals("depen value should be comma-separated", "OptionB,OptionA", result.get("depen"));
    }
    
    @Test
    public void testComplexArrayValues() throws Exception {
        // Test multiple array values
        Map<String, String> result = ParameterParsingUtils.parseParameterValues("param1:[A,B,C],param2:[X,Y,Z],single:value");

        assertEquals("Should have 3 parameters", 3, result.size());
        assertEquals("param1 should have 3 values", "A,B,C", result.get("param1"));
        assertEquals("param2 should have 3 values", "X,Y,Z", result.get("param2"));
        assertEquals("single value should be parsed", "value", result.get("single"));
    }

    @Test
    public void testArrayWithSpaces() throws Exception {
        // Test array values with spaces
        Map<String, String> result = ParameterParsingUtils.parseParameterValues("options:[Option A,Option B,Option C]");

        assertEquals("Should have 1 parameter", 1, result.size());
        assertEquals("options should preserve spaces", "Option A,Option B,Option C", result.get("options"));
    }

    @Test
    public void testEmptyArray() throws Exception {
        // Test empty array
        Map<String, String> result = ParameterParsingUtils.parseParameterValues("empty:[]");

        assertEquals("Should have 1 parameter", 1, result.size());
        assertEquals("empty array should be empty string", "", result.get("empty"));
    }
    
    @Test
    public void testSingleValueInArray() throws Exception {
        // Test single value in array brackets
        Map<String, String> result = ParameterParsingUtils.parseParameterValues("single:[OnlyOne]");

        assertEquals("Should have 1 parameter", 1, result.size());
        assertEquals("single array value should work", "OnlyOne", result.get("single"));
    }

    @Test
    public void testMalformedArrayMissingCloseBracket() throws Exception {
        // Test malformed array (missing close bracket)
        Map<String, String> result = ParameterParsingUtils.parseParameterValues("bad:[value1,value2");

        assertEquals("Should handle malformed array", 1, result.size());
        // Should treat as regular string since bracket is not closed
        assertTrue("Should contain the value", result.containsKey("bad"));
    }

    @Test
    public void testEmptyString() throws Exception {
        // Test empty string
        Map<String, String> result = ParameterParsingUtils.parseParameterValues("");

        assertEquals("Empty string should return empty map", 0, result.size());
    }

    @Test
    public void testNullString() throws Exception {
        // Test null string
        Map<String, String> result = ParameterParsingUtils.parseParameterValues(null);

        assertEquals("Null string should return empty map", 0, result.size());
    }
    
    @Test
    public void testValuesWithColons() throws Exception {
        // Test values containing colons (e.g., URLs or time formats)
        Map<String, String> result = ParameterParsingUtils.parseParameterValues("url:https://example.com,time:10:30:00");

        assertEquals("Should have 2 parameters", 2, result.size());
        assertEquals("url should preserve colons", "https://example.com", result.get("url"));
        assertEquals("time should preserve colons", "10:30:00", result.get("time"));
    }

    @Test
    public void testRealWorldScenario() throws Exception {
        // Test real-world scenario from the request
        String input = "Channel:C01,depen:[OptionB,OptionA]";
        Map<String, String> result = ParameterParsingUtils.parseParameterValues(input);

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
