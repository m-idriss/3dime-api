package com.dime.api.feature.converter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Data
@Schema(description = "Request to convert images to calendar events")
public class ConverterRequest {
    
    @NotEmpty(message = "Files list cannot be empty")
    @Size(max = 10, message = "Maximum 10 files allowed per request")
    @Schema(description = "List of image files to convert", required = true)
    public List<@Valid ImageFile> files;
    
    @Schema(description = "Timezone for event times (e.g., 'America/New_York')", examples = "UTC")
    public String timeZone;
    
    @Schema(description = "Current date in ISO-8601 format for context", examples = "2024-01-31")
    public String currentDate;
    
    @Schema(description = "User identifier for quota tracking")
    public String userId;

    @Data
    @Schema(description = "Image file to be processed")
    public static class ImageFile {
        
        @Schema(description = "Base64 encoded image data URL", examples = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQ...")
        public String dataUrl;
        
        @Schema(description = "URL to a publicly accessible image")
        public String url;
    }
}
