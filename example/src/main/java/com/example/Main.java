package com.example;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;


public class Main {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws JsonProcessingException {
        ImmutableMap<String, String> testMap = ImmutableMap.of("testKey", "testValue");

        System.out.println(objectMapper.writeValueAsString(testMap));
    }
}
