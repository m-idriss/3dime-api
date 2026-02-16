package com.threedime.api.feature.converter;

import java.util.List;

public class ConverterRequest {
    public List<ImageFile> files;
    public String timeZone;
    public String currentDate;
    public String userId;

    public static class ImageFile {
        public String dataUrl;
        public String url;
    }
}
