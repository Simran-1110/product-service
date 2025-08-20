package com.nuclei.product.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.AttributeConverter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MapJsonConverter implements AttributeConverter<Map<String, String>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(final Map<String, String> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return "{}";
    }
    try {
      return MAPPER.writeValueAsString(attribute);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("unable to convert metadata map to JSON", e);
    }
  }

  @Override
  public Map<String, String> convertToEntityAttribute(final String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return new HashMap<>();
    }
    try {
      return MAPPER.readValue(dbData, MAPPER.getTypeFactory().constructMapType(Map.class, String.class, String.class));
    } catch (IOException e) {
      return new HashMap<>();
    }
  }
}
