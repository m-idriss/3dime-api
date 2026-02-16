package com.dime.api.feature.converter;

import lombok.Data;
import java.util.List;

@Data
public class ConverterRequest {
    public List<ImageFile> files;
    public String timeZone;
    public String currentDate;
    public String userId;

    @Data
    public static class ImageFile {
        public String dataUrl;
        public String url;
    }
}
