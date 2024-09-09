package io.github.kbuntrock.resources.endpoint.spring;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/multiple-produced-content-types")
public class MultipleProducedContentTypes {

	@GetMapping(path = "/", produces = MediaType.APPLICATION_JSON_VALUE)
	public String json() {
		return "json";
	}

	@GetMapping(path = "/", produces = MediaType.TEXT_XML_VALUE)
	public String xml() {
		return "xml";
	}
}
