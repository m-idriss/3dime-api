package com.threedime.api;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;

@OpenAPIDefinition(info = @Info(title = "3Dime API", version = "1.0.0-SNAPSHOT", description = "The 3Dime API provides GitHub integration functionality", contact = @Contact(name = "3Dime API Support", url = "https://github.com/m-idriss/3dime-api"), license = @License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0.html")))
public class ThreeDimeApplication extends Application {
}