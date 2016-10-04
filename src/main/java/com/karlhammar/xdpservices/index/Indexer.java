package com.karlhammar.xdpservices.index;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.springframework.util.StringUtils;

import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;
import com.karlhammar.xdpservices.data.CodpDetails;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import pitt.search.semanticvectors.BuildIndex;

public class Indexer {

	// Singleton instance.
	public final static Indexer INSTANCE = new Indexer();

	// Singleton properties.
	private static Log log;
	private static Properties searchProperties;
	private static IndexWriter writer;
	private static IDictionary wordnetDictionary;
	
	/**
	 * Private singleton constructor setting up all the statics that are needed. 
	 */
	private Indexer() {
		// Instantiate logging
		log = LogFactory.getLog(Indexer.class);
		
		// Get indexing configuration
		try {
			searchProperties = new Properties();
			searchProperties.load(Indexer.class.getResourceAsStream("indexing.properties"));
		} 
		catch (IOException e) {
			log.fatal(String.format("Unable to load search properties. Error message: %s", e.getMessage()));
		}
		
		// Load WordNet dictionary 
		try {
			String WnDictPath = searchProperties.getProperty("wordNetPath");
			URL url = new URL("file", null, WnDictPath);
			wordnetDictionary = new Dictionary(url);
			wordnetDictionary.open();
		}
		catch (IOException ex) {
			log.error(String.format("Unable to load WordNet. Error message: %s", ex.getMessage()));
		}
	}

	
	/**
	 * Builds Lucene and SemanticVectors indexes from an ODP CSV file exported from the ODP portal, 
	 * and a file system directory containing ODP OWL files.
	 * 
	 * @return A user friendly indexing success/failure message string.
	 * @throws IOException 
	 */
	public String buildIndex() throws IOException {
		log.info("Initiating index re-build.");
		
		// Indexing configuration
		String odpRepositoryPath = searchProperties.getProperty("odpRepositoryPath");
		String vectorBasePath = searchProperties.getProperty("semanticVectorsPath");
		Path luceneIndexPath = Paths.get(searchProperties.getProperty("luceneIndexPath"));
		
		// Map that keeps track of objects parsed from CSV file for later file-based indexing
		Map<String,CodpDetails> iriToDetailsMap = new HashMap<String,CodpDetails>();
		
		// Parse ODP CSV file
		long csvStartTime = System.nanoTime();
		URL csvFileUrl = Indexer.class.getResource("ODPs.csv");
		Reader csvFileReader = new FileReader(csvFileUrl.getPath());
		Iterable<CSVRecord> records = CSVFormat.EXCEL.withDelimiter(';').withSkipHeaderRecord(true).withHeader("OWLBuildingBlock",
				"Name",
				"GraphicallyRepresentedBy",
				"HasIntent",
				"PatternDomain",
				"CoversRequirement",
				"ContentODPDescription",
				"HasConsequence",
				"Scenario").parse(csvFileReader);
		
		for (CSVRecord record : records) {
			// Add mandatory fields
		    String iri = record.get("OWLBuildingBlock");
		    String name = record.get("Name");
		    CodpDetails odpDetails = new CodpDetails(iri,name);
		    
		    // Add optional fields
		    if (record.isSet("GraphicallyRepresentedBy")) {
		    	odpDetails.setImageIri(record.get("GraphicallyRepresentedBy"));
		    }
		    if (record.isSet("HasIntent")) {
		    	odpDetails.setIntent(record.get("HasIntent"));
		    }
		    if (record.isSet("ContentODPDescription")) {
		    	odpDetails.setDescription(record.get("ContentODPDescription"));
		    }
		    if (record.isSet("HasConsequence")) {
		    	odpDetails.setConsequences(record.get("HasConsequence"));
		    }
		    	
		    // Add list fields (if they exist), splitting as needed
		    if (record.isSet("PatternDomain")) {
		    	String[] domains = record.get("PatternDomain").split("[\n\r]");
		    	for (String domain: domains) {
		    		odpDetails.getDomains().add(domain);
		    	}
		    }
		    if (record.isSet("CoversRequirement")) {
		    	String[] cqs = record.get("CoversRequirement").split("[\n\r]");
		    	for (String cq: cqs) {
		    		odpDetails.getCqs().add(cq);
		    	}
		    }
		    if (record.isSet("Scenario")) {
		    	String[] scenarios = record.get("Scenario").split("[\n\r]");
		    	for (String scenario: scenarios) {
		    		odpDetails.getScenarios().add(scenario);
		    	}
		    }
		    
		    // Add generated ODP object to map for later reference
		    iriToDetailsMap.put(iri, odpDetails);
		}
		long csvEndTime = System.nanoTime();
		float csvDuration = (csvEndTime - csvStartTime) / 1000000000;
		String csvStatus = String.format("CSV file parsed in %.1f seconds.", csvDuration);
		
		// Get filesystem reference to ODP path and do basic sanity checking
		File odpRepository = new File(odpRepositoryPath);
		if (!odpRepository.isDirectory()) {
			log.fatal(String.format("Configured ODP repository path is not a directory: %s", odpRepositoryPath));
			return "Index rebuild failed.";
		} 
		else {
			// Configure Lucene index
			long luceneStartTime = System.nanoTime();
			Directory dir = FSDirectory.open(luceneIndexPath);
			Analyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
			iwc.setOpenMode(OpenMode.CREATE);
			writer = new IndexWriter(dir, iwc);
			String[] files = odpRepository.list();
			for (int i = 0; i < files.length; i++) {
				File odpFile = new File(odpRepository, files[i]);
				if (!odpFile.isHidden() && !odpFile.isDirectory()) {
					
					OWLOntology odp;
					String odpIri;

					// Load the ODP file into an OWLOntology
					try {
			            OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
			            FileDocumentSource fds = new FileDocumentSource(odpFile);
			            config = config.setFollowRedirects(false);
			            config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
			            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
						odp = manager.loadOntologyFromOntologyDocument(fds, config);
					} 
					catch (OWLOntologyCreationException e) {
						log.error(String.format("Unable to parse ODP file %s. Error message: %s", odpFile.getAbsolutePath(), e.getMessage()));
						return null;
					}
			        
			        // Extract the ODP IRI
			        try {
			        	odpIri =  odp.getOntologyID().getOntologyIRI().toString();
			        }
			        catch (NullPointerException npe) {
			        	log.error(String.format("ODP file %s has no IRI. Error message: %s", odpFile.getAbsolutePath(), npe.getMessage()));
			        	return null;
			        }
			        
			        // Get classes and properties (using list as size is initially unknown)
		            List<String> odpClassesList = new ArrayList<String>();
		            List<String> odpPropertiesList = new ArrayList<String>();
		            
		            // Extract labels of classes and properties from ODP graph
		            Set<OWLEntity> allEntities = odp.getSignature(false);
		            for (OWLEntity anEntity: allEntities) {
		            	
		            	// By default use the local uri portion. 
		            	@SuppressWarnings("deprecation")
						String localURI = anEntity.getIRI().getFragment();
		            	
		      		  	for (CaseFormat c : CaseFormat.values())
		      		  		localURI = c.to(CaseFormat.LOWER_UNDERSCORE, localURI);
		      		  	String processedLocalURI = localURI.replace("_", " ").replace("-", " ");
		      		  	
		      		  	// If an rdfs:label is found, use that instead.
		      		  	String entityLabel;
		            	Optional<String> rdfsLabel = getRdfsLabel(anEntity,odp);
		            	if (rdfsLabel.isPresent()) {
		            		entityLabel = rdfsLabel.get();
		            	}
		            	else {
		            		entityLabel = processedLocalURI;
		            	}
		            	
		            	// Sort classes and properties into their respective lists
		            	if (anEntity instanceof OWLClass) {
		            		odpClassesList.add(entityLabel);
		            	}
		            	
		            	if (anEntity instanceof OWLObjectProperty || anEntity instanceof OWLDataProperty) {
		            		odpPropertiesList.add(entityLabel);
		            	}
			        }
		            
		            // Fetch previously retrieved data from CSV, if it exists. Then get the details
		            // from the file itself. Finally, merge the two details objects together, keeping
		            // the best of both.
			        CodpDetails odpDetailsFromCsv = iriToDetailsMap.get(odpIri);
			        if (odpDetailsFromCsv == null) {
			        	// This step is because often users publish ODPs on the portal with reference to 
			        	// an IRI building block which is actually different from the base IRI in the ODP
			        	// itself; typically the .owl ending is available in the portal but not part of 
			        	// the ODP ontology namespace.
			        	odpDetailsFromCsv = iriToDetailsMap.get(odpIri + ".owl");
			        }
			        CodpDetails odpDetailsFromFile = parseOdpDetails(odpIri, odp);
			        CodpDetails odpDetails = mergeCodpDetails(odpDetailsFromCsv, odpDetailsFromFile);
			        
		        	log.info(String.format("Indexing: %s", odpDetails.getIri()));
		        	
		        	// List of all terms
		            List<String> allTerms = new ArrayList<String>();
		            allTerms.addAll(odpClassesList);
		            allTerms.addAll(odpPropertiesList);
		        	
		        	// Make a new, empty Lucene document
		            Document doc = new Document();
		            
		            // Add IRI 
		            Field uriField = new StringField("iri", odpIri, Field.Store.YES);
		            doc.add(uriField);
		            
		            // Add name
		            String odpName = odpDetails.getName();
	    	        Field nameField = new StringField("name", odpName, Field.Store.YES);
	    	        doc.add(nameField);
	    	        allTerms.add(odpName);
		            
		            // Add path of actual building block
		            Field pathField = new StringField("path", odpFile.getCanonicalPath(), Field.Store.YES);
		            doc.add(pathField);
		            
		            // Add image
		            if (odpDetails.getImageIri().isPresent()) {
		            	Field imageField = new StringField("image", odpDetails.getImageIri().get(), Field.Store.YES);
		            	doc.add(imageField);
		            }
		            
		            // Add intent
		            if (odpDetails.getIntent().isPresent()) {
		            	String odpIntent = odpDetails.getIntent().get();
		            	Field intentField = new StringField("intent", odpIntent, Field.Store.YES);
		            	doc.add(intentField);
		            	allTerms.add(odpName);
		            }
		            
		            // Add description
		            if (odpDetails.getDescription().isPresent()) {
		            	String odpDescription = odpDetails.getDescription().get();
		            	Field descriptionField = new StringField("description", odpDescription, Field.Store.YES);
		            	doc.add(descriptionField);
		            	allTerms.add(odpDescription);
		            }
		            
		            // Add consequences
		            if (odpDetails.getConsequences().isPresent()) {
		            	String odpConsequences = odpDetails.getConsequences().get();
		            	Field consequencesField = new StringField("consequences", odpConsequences, Field.Store.YES);
		            	doc.add(consequencesField);
		            	allTerms.add(odpConsequences);
		            }
		            
		            // Add domains
		            for (String domain: odpDetails.getDomains()) {
	    	        	Field domainField = new TextField("domain", domain, Field.Store.YES);
	    	        	doc.add(domainField);
		    	    }
		            allTerms.addAll(odpDetails.getDomains());
		            
		            // Add scenarios
		            for (String scenario: odpDetails.getScenarios()) {
	    	        	Field scenarioField = new TextField("scenario", scenario, Field.Store.YES);
	    	        	doc.add(scenarioField);
		    	    }
		            allTerms.addAll(odpDetails.getScenarios());
		            
		            // Add CQ:s
		            for (String cq: odpDetails.getCqs()) {
	    	        	Field cqField = new TextField("cq", cq, Field.Store.YES);
	    	        	doc.add(cqField);
		    	    }
		            allTerms.addAll(odpDetails.getCqs());
		            
		            // Tokenize all terms, clean out whitespace, and find synonyms
		            String allTermsConcatenated = StringUtils.collectionToDelimitedString(allTerms, " ");
		            List<String> allTermsCleaned = new ArrayList<String>();
		            List<String> synonymsList = new ArrayList<String>();
		        	Analyzer wsAnalyzer = new WhitespaceAnalyzer();
		        	TokenStream tokenStream = wsAnalyzer.tokenStream(null, new StringReader(allTermsConcatenated));
		        	tokenStream.reset();
		        	while(tokenStream.incrementToken()) {
		        		// Get word (token) without whitespace
		        		String token = tokenStream.getAttribute(CharTermAttribute.class).toString();
		        		allTermsCleaned.add(token);
		        		
		                // Find synonyms for each word in WordNet
		        		synonymsList.add(token);
		        		synonymsList.addAll(getSynonyms(token));
		        	}
		        	wsAnalyzer.close();
		        	
		        	// Add all terms and synonyms to index
		            String allTermsCleanedConcatenated = StringUtils.collectionToDelimitedString(allTermsCleaned, " ");
		            doc.add(new TextField("allterms", allTermsCleanedConcatenated, Field.Store.YES));
		            String synonyms = StringUtils.collectionToDelimitedString(synonymsList, " ");
		            doc.add(new TextField("synonyms", synonyms, Field.Store.YES));
		            
		            // Write or update index
		            if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
		                // New index, so we just add the document (no old document can be there):
		                writer.addDocument(doc);
		            } 
		            else {
		                // Existing index (an old copy of this document may have been indexed) so 
		                // we use updateDocument instead to replace the old one matching the exact 
		                // uri, if present:
		                writer.updateDocument(new Term("iri", odpDetails.getIri()), doc);
		            }
				}
			}
			writer.close();
			long luceneEndTime = System.nanoTime();
			float luceneDuration = (luceneEndTime - luceneStartTime) / 1000000000;
			String luceneStatus = String.format("Lucene index rebuilt in %.1f seconds.", luceneDuration);
			log.info(luceneStatus);
			
			long vectorsStartTime = System.nanoTime();
			String termVectorsPath = String.format("%stermvectors", vectorBasePath);
			String docVectorsPath = String.format("%sdocvectors", vectorBasePath);
			String[] configurationArray = {"-contentsfields","allterms","-docindexing","inmemory","-docvectorsfile",docVectorsPath,"-termvectorsfile", termVectorsPath,"-luceneindexpath",luceneIndexPath.toString(),"-docidfield","iri","-trainingcycles","2"};
			try {
				BuildIndex.main(configurationArray);
			} catch (Exception e) {
				log.fatal(String.format("Semantic Vectors construction failed with error: %s", e.getMessage()));
				return "Semantic Vectors index construction failed.";
			}
			long vectorsEndTime = System.nanoTime();
			float vectorsDuration = (vectorsEndTime - vectorsStartTime) / 1000000000;
			String vectorsStatus = String.format("Semantic Vectors index rebuilt in %.1f seconds.", vectorsDuration);
			
			return String.format("%s<br />%s<br />%s", csvStatus, luceneStatus, vectorsStatus);
		}
	}
	
	// First is authoritative version - second is used for enrichment if needed
	private CodpDetails mergeCodpDetails(CodpDetails odpDetailsFromCsv, CodpDetails odpDetailsFromFile) {
		if (odpDetailsFromCsv == null) {
			return odpDetailsFromFile;
		}
		else {
			String odpIri = odpDetailsFromCsv.getIri();
			String odpName;
			String odpImage;
			String odpIntent;
	        String odpDescription;
	        String odpConsequence;
	        List<String> odpDomains;
	        List<String> odpScenarios;
	        List<String> odpCqs;
	        
	        
	        // Disambiguate name
	        if (odpDetailsFromCsv.getName() != null) {
	        	odpName = odpDetailsFromCsv.getName();
	        }
	        else {
	        	odpName = odpDetailsFromFile.getName();
	        }
	        
	        // Disambiguate image, intent, description, consequences
	        
	        odpImage = odpDetailsFromCsv.getImageIri().or(odpDetailsFromFile.getImageIri()).orNull();
	        odpIntent =  odpDetailsFromCsv.getIntent().or(odpDetailsFromFile.getIntent()).orNull();
	        odpDescription =  odpDetailsFromCsv.getDescription().or(odpDetailsFromFile.getDescription()).orNull();
	        odpConsequence =  odpDetailsFromCsv.getConsequences().or(odpDetailsFromFile.getConsequences()).orNull();
	        
	        // Disambiguate domains
	        if (odpDetailsFromCsv.getDomains().size() >= odpDetailsFromFile.getDomains().size()) {
	        	odpDomains = odpDetailsFromCsv.getDomains();
	        }
	        else {
	        	odpDomains = odpDetailsFromFile.getDomains();
	        }
	        
	        // Disambiguate Scenarios
	        if (odpDetailsFromCsv.getScenarios().size() >= odpDetailsFromFile.getScenarios().size()) {
	        	odpScenarios = odpDetailsFromCsv.getScenarios();
	        }
	        else {
	        	odpScenarios = odpDetailsFromFile.getScenarios();
	        }
	        
	        // Disambiguate CQs
	        if (odpDetailsFromCsv.getCqs().size() >= odpDetailsFromFile.getCqs().size()) {
	        	odpCqs = odpDetailsFromCsv.getCqs();
	        }
	        else {
	        	odpCqs = odpDetailsFromFile.getCqs();
	        }
	        
	        
	        return new CodpDetails(odpIri, odpName, odpImage, odpIntent, odpDescription, odpConsequence, odpDomains, odpScenarios, odpCqs);
		}
	}


	private CodpDetails parseOdpDetails(String odpIri, OWLOntology odp) {
		
		OWLDataFactory df = OWLManager.getOWLDataFactory();
		
		String odpName = null;
		String odpImage = null;
		List<String> odpIntents = new ArrayList<String>();
        List<String> odpDescriptions = new ArrayList<String>();
        List<String> odpConsequences = new ArrayList<String>();
        List<String> odpDomains = new ArrayList<String>();
        List<String> odpCqs = new ArrayList<String>();
        List<String> odpScenarios = new ArrayList<String>();
		
		// Get all annotations on ODP ontology
        for (OWLAnnotation annotation: odp.getAnnotations()) {
        	if (annotation.getProperty().getIRI().equals(df.getRDFSLabel().getIRI()) && annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		odpName = val.getLiteral();
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://www.ontologydesignpatterns.org/schemas/cpannotationschema.owl#hasIntent")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		odpIntents.add(val.getLiteral());
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://xd-protege.com/schemas/cpas-ext.owl#solutionDescription")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		odpDescriptions.add(val.getLiteral());
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://www.ontologydesignpatterns.org/schemas/cpannotationschema.owl#hasConsequences")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		odpConsequences.add(val.getLiteral());
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://xd-protege.com/schemas/cpas-ext.owl#category")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		odpDomains.add(val.getLiteral());
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://www.ontologydesignpatterns.org/schemas/cpannotationschema.owl#coversRequirements")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		String cqValue = val.getLiteral();
        		for (String cqPart: cqValue.split("\\?")) {
        			cqPart = cqPart.trim();
        			if (cqPart.length() > 1) {
	        			String cqPartCapitalized = cqPart.substring(0,1).toUpperCase() + cqPart.substring(1);
	        			odpCqs.add(String.format("%s?",cqPartCapitalized));
        			}
        			
        		}
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://xd-protege.com/schemas/cpas-ext.owl#hasImage")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		odpImage = val.getLiteral();
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://www.ontologydesignpatterns.org/schemas/cpannotationschema.owl#scenarios")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		String scenario = val.getLiteral();
        		if (scenario.length() > 1) {
        			scenario = scenario.substring(0,1).toUpperCase() + scenario.substring(1);
        		}
        		odpScenarios.add(scenario);
        	}
        }
        
        // In the case that no rdfs:label exists, construct ODP name from ODP IRI
        try {
	        if (odpName == null) {
	        	odpName = odpIri;
	        	if (odpIri.endsWith("/")) {
	        		// Remove trailing slash
	        		odpName = odpIri.substring(0, odpIri.lastIndexOf("/")-1);
	        	}
	        	odpName = odpName.substring(odpIri.lastIndexOf("/")+1);
	        }
        }
        catch (Exception e) {
        	odpName = "Unknown";
        }
        
        // Merge multivalued fields
        String odpIntent = StringUtils.collectionToDelimitedString(odpIntents, "\n\n");
        String odpDescription = StringUtils.collectionToDelimitedString(odpDescriptions, "\n\n");
        String odpConsequence = StringUtils.collectionToDelimitedString(odpConsequences, "\n\n");
        
		return new CodpDetails(odpIri,odpName,odpImage,odpIntent,odpDescription,odpConsequence,odpDomains,odpScenarios,odpCqs);
	}


	private static Optional<String> getRdfsLabel(OWLEntity entity, OWLOntology ontology) {
		Set<OWLAnnotation> annotations = entity.getAnnotations(ontology);
		for (OWLAnnotation annotation: annotations) {
			if (annotation.getProperty().isLabel() && annotation.getValue() instanceof OWLLiteral) {
				OWLLiteral annotationAsLiteral = (OWLLiteral) annotation.getValue();
      			String annotationLang = annotationAsLiteral.getLang();
      			if (annotationLang == "en" || annotationLang == "EN" || annotationLang == "") { 
      				return Optional.of(annotationAsLiteral.getLiteral());
      			}
  			}
		}
		return Optional.absent();
	}
	
	public static List<String> getSynonyms(String inputWord) {
		List<String> synonyms = new ArrayList<String>();
		IIndexWord idxWord = wordnetDictionary.getIndexWord(inputWord, POS.NOUN);
		if (idxWord != null) {
			IWordID wordID = idxWord.getWordIDs().get(0);
			IWord word = wordnetDictionary.getWord(wordID);
			ISynset synset = word.getSynset();
			for (IWord w: synset.getWords()) {
				synonyms.add(w.getLemma());
			}
		}
		return synonyms;
	}
	
}
