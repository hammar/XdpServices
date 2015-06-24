package com.karlhammar.xdpservices.index;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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

import pitt.search.semanticvectors.BuildIndex;

import com.google.common.base.CaseFormat;
import com.karlhammar.xdpservices.search.CompositeSearch;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.stanford.bmir.protege.web.shared.xd.OdpDetails;

public class Indexer {

	// Singleton instance.
	public final static Indexer INSTANCE = new Indexer();

	// Singleton properties.
	private static Log log;
	private static Set<String> stopwords;
	private static Properties searchProperties;
	private static IndexWriter writer;
	private static IDictionary wordnetDictionary;
	private static Boolean useWordNet;

	/**
	 * Private singleton constructor setting up all the statics that are needed. 
	 */
	private Indexer() {
		log = LogFactory.getLog(Indexer.class);
		loadSearchProperties();
		stopwords = loadStopWords();
		loadWordNetDictionary();
		//loadLuceneReader();
	}
	
	
	private static void loadWordNetDictionary() {
		try {
			String WnDictPath = searchProperties.getProperty("wordNetPath");
			URL url = new URL("file", null, WnDictPath);
			wordnetDictionary = new Dictionary(url);
			wordnetDictionary.open();
			useWordNet = true;
		}
		catch (IOException ex) {
			log.error(String.format("Unable to load WordNet. WordNet-based index synonym/hypernym enrichment disabled. Error message: %s", ex.getMessage()));
			useWordNet = false;
		}
	}
	
	
	private static Set<String> loadStopWords() {
		try {
			InputStream is = CompositeSearch.class.getResourceAsStream("stopwords.txt");
			BufferedReader stopbr = new BufferedReader(new InputStreamReader(is, "UTF-8"));
			stopwords = new HashSet<String>();
			String stopLine = null;
			while ((stopLine = stopbr.readLine()) != null) {
				stopwords.add(stopLine);
			}
			stopbr.close();
			return stopwords;
		}
		catch (IOException e) {
			log.error("Unable to load stop word set; using empty stop word set.");
			return new HashSet<String>();
		}
	}
	
	
	/**
	 * Load search property file (should this functionality be in a utility class?)
	 */
	private static void loadSearchProperties() {
		try {
			searchProperties = new Properties();
			searchProperties.load(CompositeSearch.class.getResourceAsStream("search.properties"));
		} 
		catch (IOException e) {
			log.fatal(String.format("Unable to load search properties. Error message: %s", e.getMessage()));
		}
	}

	
	/**
	 * Builds Lucene index from the contents of the ODP repository. This wrapper
	 * method just finds the repository directory and iterates over the files
	 * therein; the real indexing work is done in the indexODP() method.
	 * 
	 * @return A user friendly indexing success/failure message string.
	 */
	public String buildIndex() {
		log.info("Initiating index re-build.");
		
		String odpRepositoryPath = searchProperties.getProperty("odpRepositoryPath");
		Path luceneIndexPath = Paths.get(searchProperties.getProperty("luceneIndexPath"));
		File odpRepository = new File(odpRepositoryPath);
		if (!odpRepository.isDirectory()) {
			log.fatal(String.format(
					"Configured ODP repository path is not a directory: %s",
					odpRepositoryPath));
			return "Index rebuild failed.";
		} else {
			try {
				// First build Lucene index
				long luceneStartTime = System.nanoTime();
				Directory dir = FSDirectory.open(luceneIndexPath);
				Analyzer analyzer = new StandardAnalyzer();
				IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
				iwc.setOpenMode(OpenMode.CREATE);
				writer = new IndexWriter(dir, iwc);
				String[] files = odpRepository.list();
				for (int i = 0; i < files.length; i++) {
					File candidateOdpFile = new File(odpRepository, files[i]);
					if (!candidateOdpFile.isHidden() && !candidateOdpFile.isDirectory()) {
						OdpDetails odp = parseOdp(candidateOdpFile);
						if (odp != null) {
							indexOdp(odp, candidateOdpFile);
						}
					}
				}
				writer.close();
				long luceneEndTime = System.nanoTime();
				float luceneDuration = (luceneEndTime - luceneStartTime) / 1000000000;
				String luceneStatus = String.format("Lucene index rebuilt in %.1f seconds.", luceneDuration);
				log.info(luceneStatus);
				
				// Then build Semantic Vectors index
				String vectorsStatus = buildVectorsIndex();
				log.info(vectorsStatus);
				
				return String.format("%s %s",luceneStatus,vectorsStatus);
			} 
			catch (IOException ex) {
				log.fatal(String
						.format("IO operations on index path %s failed. Error message: %s",
								ex.getMessage()));
				return "Index rebuild failed";
			}
		}
	}
	
	
	private static String buildVectorsIndex() {
		long vectorsStartTime = System.nanoTime();
		String luceneIndexPath = searchProperties.getProperty("luceneIndexPath");
		String vectorBasePath = searchProperties.getProperty("semanticVectorsPath");
		String termVectorsPath = String.format("%stermvectors", vectorBasePath);
		String docVectorsPath = String.format("%sdocvectors", vectorBasePath);
		String[] configurationArray = {"-contentsfields","allterms","-docindexing","inmemory","-docvectorsfile",docVectorsPath,"-termvectorsfile", termVectorsPath,"-luceneindexpath",luceneIndexPath,"-docidfield","uri","-trainingcycles","2"};
		try {
			BuildIndex.main(configurationArray);
		} catch (Exception e) {
			log.fatal(String.format("Semantic Vectors construction failed with error: %s", e.getMessage()));
			return "Semantic Vectors index construction failed.";
		}
		long vectorsEndTime = System.nanoTime();
		float vectorsDuration = (vectorsEndTime - vectorsStartTime) / 1000000000;
		return String.format("Semantic Vectors index rebuilt in %.1f seconds.", vectorsDuration);
	}
	

	
	/**
	 * Performs Lucene indexing (using shared static index writer) of a given ODP.
	 * @param odpFile File to add to index.
	 * @throws IOException 
	 */
	private static void indexOdp(OdpDetails odp, File odpOnDisk) throws IOException {
		
		log.info(String.format("Indexing: %s", odp.getUri()));
		
		// Make a new, empty Lucene document
        Document doc = new Document();
        
        // Store path of actual building block
        Field pathField = new StringField("path", odpOnDisk.getCanonicalPath(), Field.Store.YES);
        doc.add(pathField);
        
        // Add URI 
        Field uriField = new StringField("uri", odp.getUri(), Field.Store.YES);
        doc.add(uriField);
        
        // Add name
        if (odp.getName() != null) {
	        Field nameField = new StringField("name", odp.getName(), Field.Store.YES);
	        doc.add(nameField);
        }
        
        // Add CQ:s
        if (odp.getCqs() != null) {
	        for (String cq: odp.getCqs()) {
	        	Field cqField = new StringField("cqs", cq, Field.Store.YES);
	        	doc.add(cqField);
	        }
        }
        
        // Add description
        if (odp.getDescription() != null) {
        	Field descriptionField = new TextField("description", odp.getDescription(), Field.Store.YES);
        	doc.add(descriptionField);
        }
        
        // Add image
        if (odp.getImage() != null) {
        	Field imageField = new StringField("image", odp.getImage(), Field.Store.YES);
        	doc.add(imageField);
        }

        // Add classes
        if (odp.getClass() != null) {
	        for (String aClass: odp.getClasses()) {
	        	Field classesField = new TextField("classes", aClass, Field.Store.YES);
	        	doc.add(classesField);
	        }
        }
        
        // Add properties
        if (odp.getProperties() != null) {
	        for (String aProperty: odp.getProperties()) {
	        	Field propertiesField = new StringField("properties", aProperty, Field.Store.YES);
	        	doc.add(propertiesField);
	        }
        }
        
        // Add "all terms" catch-all field
        // Merge all terms
        String allTermsMerged = "";
        if (odp.getDescription() != null) {
        	allTermsMerged += odp.getDescription() + " ";
        }
        if (odp.getCqs() != null) {
        	for (String cq: odp.getCqs()) {
        		allTermsMerged += cq + " ";
        	}
        }
        if (odp.getClasses() != null) {
        	allTermsMerged += StringUtils.arrayToDelimitedString(odp.getClasses(), " ") + " ";
        }
        if (odp.getProperties() != null) {
        	allTermsMerged += StringUtils.arrayToDelimitedString(odp.getProperties(), " ") + " ";
        }
        
        // Junk character and stop-word removal
        String allTerms = "";
        for (String s: allTermsMerged.split(" ")) {
        	s = s.replaceAll("[^A-Za-z0-9 ]", "");
        	if (!stopwords.contains(s) && !s.equalsIgnoreCase("")) {
        		allTerms += (s + " ");
        	}
        }
        Field allTermsField = new TextField("allterms", allTerms, Field.Store.NO);
        doc.add(allTermsField);
        
        // If WordNet is enabled: find synonyms for each ODP word in WordNet 
        // and add to index as one space-delimited string of synonym terms.
        if (useWordNet) {
        	String synonyms = "";
        	for (String odpWord: allTerms.split(" ")) {
        		IIndexWord idxWord = wordnetDictionary.getIndexWord(odpWord, POS.NOUN);
        		if (idxWord != null) {
        			IWordID wordID = idxWord.getWordIDs().get(0);
        			IWord word = wordnetDictionary.getWord(wordID);
        			ISynset synset = word.getSynset();
        			for (IWord w: synset.getWords()) {
        				synonyms += " " + w.getLemma();
        			}
        		}
        	}
        	doc.add(new TextField("synonyms", synonyms, Field.Store.YES));
        }
        
        // Write or update index
        if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
            // New index, so we just add the document (no old document can be there):
            writer.addDocument(doc);
        } 
        else {
            // Existing index (an old copy of this document may have been indexed) so 
            // we use updateDocument instead to replace the old one matching the exact 
            // uri, if present:
            writer.updateDocument(new Term("uri", odp.getUri()), doc);
        }
	}
	
	
	private static String getLabel(OWLEntity entity, OWLOntology ontology) {
		Set<OWLAnnotation> annotations = entity.getAnnotations(ontology);
		for (OWLAnnotation annotation: annotations) {
			if (annotation.getProperty().isLabel() && annotation.getValue() instanceof OWLLiteral) {
				OWLLiteral annotationAsLiteral = (OWLLiteral) annotation.getValue();
      			String annotationLang = annotationAsLiteral.getLang();
      			if (annotationLang == "en" || annotationLang == "EN" || annotationLang == "") { 
      				return annotationAsLiteral.getLiteral();
      			}
  			}
		}
		return null;
	}
	
	
	/**
	 * Parse an ODP file on disk into an OdpDetails object containing the metadata about
	 * that ODP, by mapping the cpannotationschema and cpas-ext annotations on the OWL ontology 
	 * of the file in question against OdpDetails class fields.
	 * 
	 * Note that this method can return null if parsing fails, so make sure to check for this if using
	 * the method. Also note that individual OdpDetails member fields may be null if
	 * no appropriate annotations exist on the parsed ontology.
	 * @param odpFile File to parse
	 * @return An OdpDetails object or null if parsing failed.
	 */
	private static OdpDetails parseOdp(File odpFile) {
		
		// Fields to be filled by subsequent parsing.
		String odpUri = null;
		String odpName = null;
		String odpDescription = null;
		String[] odpDomains;
		String[] odpCqs;
		String odpImage = null;
		String[] odpScenarios;
		String[] odpClasses;
		String[] odpProperties;
		
		// ODP ontology
		OWLOntology odp;
		OWLDataFactory df = OWLManager.getOWLDataFactory();
		
        try {
            OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
            FileDocumentSource fds = new FileDocumentSource(odpFile);
            config = config.setFollowRedirects(false);
            config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
			odp = manager.loadOntologyFromOntologyDocument(fds, config);
		} catch (OWLOntologyCreationException e) {
			log.error(String.format("Unable to parse ODP file %s. Error message: %s", odpFile.getAbsolutePath(), e.getMessage()));
			return null;
		}
        
        // Get ODP Uri
        try {
        	odpUri =  odp.getOntologyID().getOntologyIRI().toString();
        }
        catch (NullPointerException npe) {
        	log.error(String.format("ODP file %s has no IRI. Error message: %s", odpFile.getAbsolutePath(), npe.getMessage()));
        	return null;
        }

        // Stuff needed for parsing and merging the ODP description field
        List<String> odpIntents = new ArrayList<String>();
        List<String> odpSolutions = new ArrayList<String>();
        List<String> odpConsequences = new ArrayList<String>();
        
        // Other annotation stuff. Lists rather than arrays used here
        // as the number of elements is initially unknown
        List<String> odpDomainsList = new ArrayList<String>();
        List<String> odpCqsList = new ArrayList<String>();
        List<String> odpScenariosList = new ArrayList<String>();
        
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
        		odpSolutions.add(val.getLiteral());
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://www.ontologydesignpatterns.org/schemas/cpannotationschema.owl#hasConsequences")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		odpConsequences.add(val.getLiteral());
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://xd-protege.com/schemas/cpas-ext.owl#category")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		odpDomainsList.add(val.getLiteral());
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://www.ontologydesignpatterns.org/schemas/cpannotationschema.owl#coversRequirements")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		odpCqsList.add(val.getLiteral());
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://xd-protege.com/schemas/cpas-ext.owl#hasImage")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		odpImage = val.getLiteral();
        	}
        	if (annotation.getProperty().getIRI().equals(IRI.create("http://www.ontologydesignpatterns.org/schemas/cpannotationschema.owl#scenarios")) &&
        			annotation.getValue() instanceof OWLLiteral) {
        		OWLLiteral val = (OWLLiteral) annotation.getValue();
        		odpScenariosList.add(val.getLiteral());
        	}
        }
        
        // In the case that no rdfs:label exists, construct odp name from ODP uri
        try {
	        if (odpName == null) {
	        	odpName = odpUri;
	        	if (odpUri.endsWith("/")) {
	        		// Remove trailing slash
	        		odpName = odpUri.substring(0, odpUri.lastIndexOf("/")-1);
	        	}
	        	odpName = odpName.substring(odpUri.lastIndexOf("/")+1);
	        }
        }
        catch (Exception e) {
        	odpName = "Unknown";
        }
        
        // Merge intents, solutions, and consequences into one field in structured manner.
        String odpIntent = StringUtils.collectionToDelimitedString(odpIntents, "\n\n");
        String odpSolution = StringUtils.collectionToDelimitedString(odpSolutions, "\n\n");
        String odpConsequence = StringUtils.collectionToDelimitedString(odpConsequences, "\n\n");
        odpDescription = StringUtils.arrayToDelimitedString(new String[]{odpIntent,odpSolution,odpConsequence}, "\n\n");
        
        // Transform lists of domains and cqs into arrays as required by target object
        odpDomains = odpDomainsList.toArray(new String[odpDomainsList.size()]);
        odpCqs = odpCqsList.toArray(new String[odpCqsList.size()]);
        odpScenarios = odpScenariosList.toArray(new String[odpScenariosList.size()]);
        
        // Get classes and properties (using list as size is initially unknown)
        List<String> odpClassesList = new ArrayList<String>();
        List<String> odpPropertiesList = new ArrayList<String>();
        
        // Extract labels of classes and properties from ODP graph
        Set<OWLEntity> allEntities = odp.getSignature(false);
        for (OWLEntity anEntity: allEntities) {
        	
        	// By default use the local uri portion. 
        	String globalURI =  anEntity.getIRI().toString();
        	String localURI = globalURI.substring(globalURI.replace("#", "/").lastIndexOf("/") + 1);
  		  	for (CaseFormat c : CaseFormat.values())
  		  		localURI = c.to(CaseFormat.LOWER_UNDERSCORE, localURI);
  		  	String processedLocalURI = localURI.replace("_", " ").replace("-", " ");
  		  	
  		  	// If an rdfs:label is found, use that instead.
        	String entityLabel;
        	if (getLabel(anEntity,odp) != null) {
        		entityLabel = getLabel(anEntity,odp);
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
        
        // Transform lists of class and property names to arrays as required by target object
        odpClasses = odpClassesList.toArray(new String[odpClassesList.size()]);
        odpProperties = odpPropertiesList.toArray(new String[odpPropertiesList.size()]);
        
        return new OdpDetails(odpUri,odpName,odpDescription,odpDomains,odpCqs,odpImage,odpScenarios,odpClasses,odpProperties);
	}
	
}