package com.dime.api.feature.converter;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(description = "Response from image to calendar conversion")
public class ConverterResponse {

    @Schema(description = "Whether the conversion was successful", examples = "true")
    public boolean success;

    @Schema(description = "The generated ICS calendar content", examples = "BEGIN:VCALENDAR...")
    public String icsContent;

    @Schema(description = "Error message if conversion failed")
    public String error;

    @Schema(description = "Detailed error description")
    public String message;

    @Schema(description = "Additional error details")
    public Object details;

    public ConverterResponse(boolean success, String icsContent) {
        this.success = success;
        this.icsContent = icsContent;
    }
}
