package info.laplace.awsjb.springbootapp;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class App {
	private final static Logger logger = LoggerFactory.getLogger(App.class);
	
	public static void main(String[] args) {
		SpringApplication.run(App.class, args);

	}
	
	@GetMapping({ "/", "/hello" })
	public String helloGet(HttpServletRequest request)  throws Exception {
		return helloPost(request);
	}
	
	@PostMapping({ "/", "/hello" })
	public String helloPost(HttpServletRequest request) throws Exception {
		String finalResponse = "Hello world.";
		
		//Logging exception.
		Exception el = new  Exception("Test Stacktrace CWL.");
		logger.error("Stack form Logger",el);
		

		return finalResponse;
	}

}
