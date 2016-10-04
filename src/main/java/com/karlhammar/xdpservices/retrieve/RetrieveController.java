package com.karlhammar.xdpservices.retrieve;

import java.io.IOException;

import org.apache.lucene.queryparser.classic.ParseException;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karlhammar.xdpservices.data.CodpDetails;

//import edu.stanford.bmir.protege.web.shared.xd.OdpDetails;

@RestController
public class RetrieveController {
    
    @RequestMapping("/retrieve/odpMetadata")
    public CodpDetails getOdpMetadata(@RequestParam(value="uri", required=true)String uri) {
    	return MetadataFetcher.INSTANCE.getOdpDetails(uri);
    }
	
    @RequestMapping("/retrieve/odpMetadataByCategory")
    public CodpDetails[] odpsByCategory(@RequestParam(value="category", required=true) String category) {
    	return MetadataFetcher.INSTANCE.getOdpsByCategory(category);
    }
    
    @RequestMapping("/retrieve/odpCategories")
    public String[] odpCategories() {
    	return MetadataFetcher.INSTANCE.getOdpCategories();
    }
    
	@RequestMapping("/retrieve/odpBuildingBlockTurtle")
	public String getOdpBuildingBlockTurtle(@RequestParam(value="uri", required=true)String odpIri) throws OWLOntologyCreationException, OWLOntologyStorageException, ParseException, IOException {
		return OdpFetcher.getOdpBuildingBlockTurtle(odpIri);
	}
}
