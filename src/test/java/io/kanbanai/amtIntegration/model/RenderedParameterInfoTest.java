package io.kanbanai.amtIntegration.model;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test class for RenderedParameterInfo
 * 
 * Tests the new data field functionality for DynamicReferenceParameter
 */
public class RenderedParameterInfoTest {
    
    @Test
    public void testDataFieldGetterSetter() {
        RenderedParameterInfo paramInfo = new RenderedParameterInfo();
        
        // Test initial state
        assertNull("Data should be null initially", paramInfo.getData());
        assertFalse("hasData() should return false initially", paramInfo.hasData());
        
        // Test setting data
        String htmlData = "<input type=\"hidden\" name=\"value\" value=\"test\" />";
        paramInfo.setData(htmlData);
        
        assertEquals("Data should be set correctly", htmlData, paramInfo.getData());
        assertTrue("hasData() should return true when data is set", paramInfo.hasData());
        
        // Test setting empty data
        paramInfo.setData("");
        assertFalse("hasData() should return false for empty string", paramInfo.hasData());
        
        // Test setting whitespace-only data
        paramInfo.setData("   ");
        assertFalse("hasData() should return false for whitespace-only string", paramInfo.hasData());
    }
    
    @Test
    public void testJsonSerializationWithData() {
        RenderedParameterInfo paramInfo = new RenderedParameterInfo();
        paramInfo.setName("testParam");
        paramInfo.setType("DynamicReferenceParameter");
        paramInfo.setDescription("Test parameter");
        paramInfo.setCurrentValue("testValue");
        paramInfo.setInputType(ParameterInputType.DYNAMIC_REFERENCE);
        paramInfo.setDynamic(true);
        paramInfo.setData("<input type=\"hidden\" name=\"value\" value=\"test\" />");
        
        String json = paramInfo.toJson();
        
        // Verify that JSON contains the data field
        assertTrue("JSON should contain data field", json.contains("\"data\":"));
        assertTrue("JSON should contain the HTML data", json.contains("input type=\\\"hidden\\\""));
        
        // Verify other fields are still present
        assertTrue("JSON should contain name", json.contains("\"name\":\"testParam\""));
        assertTrue("JSON should contain type", json.contains("\"type\":\"DynamicReferenceParameter\""));
        assertTrue("JSON should contain inputType", json.contains("\"inputType\":\"dynamic_reference\""));
    }
    
    @Test
    public void testJsonSerializationWithoutData() {
        RenderedParameterInfo paramInfo = new RenderedParameterInfo();
        paramInfo.setName("testParam");
        paramInfo.setType("ChoiceParameter");
        paramInfo.addChoice("Option1");
        paramInfo.addChoice("Option2");
        
        String json = paramInfo.toJson();
        
        // Verify that JSON contains data field as null
        assertTrue("JSON should contain data field as null", json.contains("\"data\":null"));
        
        // Verify choices are present
        assertTrue("JSON should contain choices", json.contains("\"choices\":[\"Option1\",\"Option2\"]"));
    }
    
    @Test
    public void testToStringWithData() {
        RenderedParameterInfo paramInfo = new RenderedParameterInfo();
        paramInfo.setName("testParam");
        paramInfo.setType("DynamicReferenceParameter");
        paramInfo.setData("<input type=\"hidden\" />");
        
        String toString = paramInfo.toString();
        
        // Verify that toString includes hasData information
        assertTrue("toString should include hasData=true", toString.contains("hasData=true"));
        
        // Test without data
        paramInfo.setData(null);
        toString = paramInfo.toString();
        assertTrue("toString should include hasData=false", toString.contains("hasData=false"));
    }
    
    @Test
    public void testChoicesVsDataSeparation() {
        RenderedParameterInfo paramInfo = new RenderedParameterInfo();
        
        // Test that choices and data are independent
        paramInfo.addChoice("Choice1");
        paramInfo.setData("<input type=\"hidden\" />");
        
        assertTrue("Should have choices", paramInfo.hasChoices());
        assertTrue("Should have data", paramInfo.hasData());
        assertEquals("Should have 1 choice", 1, paramInfo.getChoices().size());
        assertEquals("Choice should be correct", "Choice1", paramInfo.getChoices().get(0));
        assertTrue("Data should contain input tag", paramInfo.getData().contains("input"));
    }
}
