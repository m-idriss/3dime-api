package com.dime.api.feature.converter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConverterResponse {
    public boolean success;
    public String icsContent;
    public String error;
    public String message;
    public Object details;

    public ConverterResponse(boolean success, String icsContent) {
        this.success = success;
        this.icsContent = icsContent;
    }

    public static ConverterResponse error(String error, String message) {
        ConverterResponse response = new ConverterResponse();
        response.success = false;
        response.error = error;
        response.message = message;
        return response;
    }
}
