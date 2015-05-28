package com.karlhammar.xdpservices.retrieve;

import java.io.ByteArrayOutputStream;

import org.coode.owlapi.turtle.TurtleOntologyFormat;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyFormat;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetrieveController {
	
	@RequestMapping("/retrieve/odpBuildingBlockTurtle")
	public String getOdpBuildingBlockTurtle(String odpIri) throws OWLOntologyCreationException, OWLOntologyStorageException {
		
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		
		// Load ODP. Format is guessed automatically.
		// TODO: Construct document IRI to fetch ODP from disk using Lucene indices etc.
		IRI iri = IRI.create("http://www.dcs.bbk.ac.uk/~michael/sw/slides/pizza.owl");
        OWLOntology odp = manager.loadOntologyFromOntologyDocument(iri);
        
        // Set up output format. Copy prefixes from existing file if needed.
        OWLOntologyFormat format = manager.getOntologyFormat(odp);
        TurtleOntologyFormat turtleFormat = new TurtleOntologyFormat();
        if (format.isPrefixOWLOntologyFormat()) {
        	turtleFormat.copyPrefixesFrom(format.asPrefixOWLOntologyFormat());
        }
        
        // Save ontology into Turtle format into an output stream, send to client
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        manager.saveOntology(odp, turtleFormat, baos);
        return baos.toString();
	}
}
