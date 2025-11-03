# JamnWebServiceProvider

This is a sample implementation of easy to use, REST-like Web Services exposed through a JamnServer-Content-Provider.

Like usual the <a href="/org.isa.ipc.JamnWebServiceProvider/src/main/java/org/isa/ipc/JamnWebServiceProvider.java">Provider</a> allows you to implement and register Web Services as annotated methods of a pojo class.
(<a href="/org.isa.ipc.JamnWebServiceProvider/src/test/java/org/isa/ipc/WebServiceProviderTest.java">Test </a> -- <a href="/org.isa.ipc.JamnWebServiceProvider/src/main/java/org/isa/ipc/sample/web/api/SampleWebApiServices.java"> Sample API</a>)

```java
//the service annotation
import org.isa.ipc.JamnWebServiceProvider.WebService;

// register Web Services by the provider like
// provider.registerServices(SampleWebApiServices.class);

public class SampleWebApiServices {

	//a service with native string data
	@WebService(path = "/echo", methods = { "POST" }, contentType = "text/plain")
	public String sendEcho(String pRequest) {
		return "ECHO: " + pRequest;
	}

	//a service with user defined data types
	@WebService(path = "/get-details", methods = { "POST" }, contentType = "application/json")
	public DetailsResponse sendDetailsFor(DetailsRequest pRequest) {
		DetailsResponse lResponse = new DetailsResponse();

		//service code

		return lResponse;
	}
}

// your data types
public static class DetailsRequest {
...
}

public static class DetailsResponse {
...
}

```
<br>
Required dependencies are just the JamnServer and your favorite JSON Tool:

```xml
<dependencies>
	<dependency>
		<groupId>org.isa.ipc</groupId>
		<artifactId>org.isa.ipc.JamnServer</artifactId>
		<version>0.0.1-SNAPSHOT</version>
	</dependency>
	
	<!-- or a tool of your choice -->
	<dependency>
		<groupId>com.fasterxml.jackson.core</groupId>
		<artifactId>jackson-databind</artifactId>
		<version>2.17.2</version>
	</dependency>
</dependencies>
```
