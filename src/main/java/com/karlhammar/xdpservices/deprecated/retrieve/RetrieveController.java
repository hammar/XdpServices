package com.karlhammar.xdpservices.deprecated.retrieve;

import java.io.IOException;

import org.apache.lucene.queryparser.classic.ParseException;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karlhammar.xdpservices.data.CodpDetails;

//import edu.stanford.bmir.protege.web.shared.xd.OdpDetails;

public class RetrieveController {
    
    public CodpDetails getOdpMetadata(String uri) {
    	return MetadataFetcher.INSTANCE.getOdpDetails(uri);
    }
	
    public CodpDetails[] odpsByCategory(String category) {
    	return MetadataFetcher.INSTANCE.getOdpsByCategory(category);
    }
    
    public String[] odpCategories() {
    	return MetadataFetcher.INSTANCE.getOdpCategories();
    }
    
	public String getOdpBuildingBlockTurtle(String odpIri) throws OWLOntologyCreationException, OWLOntologyStorageException, ParseException, IOException {
		return OdpFetcher.getOdpBuildingBlockTurtle(odpIri);
	}
}
