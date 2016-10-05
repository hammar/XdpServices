package com.karlhammar.xdpservices.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Optional;

public class CodpDetails implements Serializable {

	private static final long serialVersionUID = -5187426776966355376L;

	private String iri;
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
	
	public CodpDetails(String iri, String name) {
		this.iri = iri;
		this.name = name;
		this.domains = new ArrayList<String>();
		this.scenarios = new ArrayList<String>();
		this.cqs = new ArrayList<String>();
	}
	
	public CodpDetails(String iri, String name, String imageIri, String intent, String description, 
			String consequences, List<String> domains, List<String> scenarios, List<String> cqs) {
		this(iri,name);
		this.imageIri = imageIri;
		this.intent = intent;
		this.description = description;
		this.consequences = consequences;
		this.domains = domains;
		this.scenarios = scenarios;
		this.cqs = cqs;
	}

	// Getters of mandatory fields
	public String getIri() {
		return iri;
	}

	public String getName() {
		return name;
	}

	// Getters of optional fields
	public Optional<String> getImageIri() {
		return Optional.fromNullable(imageIri);
	}

	public Optional<String> getIntent() {
		return Optional.fromNullable(intent);
	}

	public Optional<String> getDescription() {
		return Optional.fromNullable(description);
	}

	public Optional<String> getConsequences() {
		return Optional.fromNullable(consequences);
	}

	// Getters of lists
	public List<String> getDomains() {
		return domains;
	}

	public List<String> getScenarios() {
		return scenarios;
	}

	public List<String> getCqs() {
		return cqs;
	}

	// Setters of optional fields
	public void setImageIri(String imageIri) {
		this.imageIri = imageIri;
	}

	public void setIntent(String intent) {
		this.intent = intent;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setConsequences(String consequences) {
		this.consequences = consequences;
	}
}
