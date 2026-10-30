package com.dime.api.feature.converter;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "Successful image-to-calendar conversion",
        requiredProperties = { "success", "icsContent" })
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

    @Schema(description = "Non-blocking review warnings produced during calendar normalization")
    public List<String> warnings;

    public ConverterResponse(boolean success, String icsContent) {
        this.success = success;
        this.icsContent = icsContent;
        this.warnings = List.of();
    }

    public ConverterResponse(boolean success, String icsContent, List<String> warnings) {
        this.success = success;
        this.icsContent = icsContent;
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
