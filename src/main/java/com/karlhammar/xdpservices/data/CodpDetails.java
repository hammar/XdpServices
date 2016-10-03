package com.karlhammar.xdpservices.data;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CodpDetails implements Serializable {

	private static final long serialVersionUID = -5187426776966355376L;

	private URI uri;
	private String name;
	private String imageIri;
	private String intent;
	private String description;
	private String consequences;
	private List<String> domains;
	private List<String> scenarios;
	private List<String> cqs;
	
	/**
	 * GWT-RPC-required empty constructor.
	 */
	@SuppressWarnings("unused")
	private CodpDetails() {
	}
	
	public CodpDetails(URI uri, String name) {
		this.uri = uri;
		this.name = name;
		this.domains = new ArrayList<String>();
		this.scenarios = new ArrayList<String>();
		this.cqs = new ArrayList<String>();
	}
	
	public CodpDetails(URI uri, String name, String imageIri, String intent, String description, 
			String consequences, List<String> domains, List<String> scenarios, List<String> cqs) {
		this(uri,name);
		this.imageIri = imageIri;
		this.domains = domains;
		this.intent = intent;
		this.description = description;
		this.consequences = consequences;
		this.scenarios = scenarios;
		this.cqs = cqs;
	}

	public URI getUri() {
		return uri;
	}

	public String getName() {
		return name;
	}

	public Optional<String> getImageIri() {
		return Optional.ofNullable(imageIri);
	}

	public Optional<String> getIntent() {
		return Optional.ofNullable(intent);
	}

	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	public Optional<String> getConsequences() {
		return Optional.ofNullable(consequences);
	}

	public List<String> getDomains() {
		return domains;
	}

	public List<String> getScenarios() {
		return scenarios;
	}

	public List<String> getCqs() {
		return cqs;
	}
}
