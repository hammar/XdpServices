package com.karlhammar.xdpservices.retrieve;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;

import com.karlhammar.xdpservices.search.CompositeSearch;

import edu.stanford.bmir.protege.web.shared.xd.OdpDetails;

public class MetadataFetcher {

	// Singleton instance.
	public final static MetadataFetcher INSTANCE = new MetadataFetcher();

	// Singleton properties.
	private static Log log;
	private static Properties searchProperties;
	private static IndexReader luceneReader;
	private static IndexSearcher luceneSearcher;
	
	/**
	 * Private singleton constructor setting up all the statics that are needed. 
	 */
	private MetadataFetcher() {
		log = LogFactory.getLog(MetadataFetcher.class);
		loadSearchProperties();
		loadLuceneReader();
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
	
	private static void loadLuceneReader() {
		try {
			Path luceneIndexPath = Paths.get(searchProperties.getProperty("luceneIndexPath"));
			luceneReader = DirectoryReader.open(FSDirectory.open(luceneIndexPath));
			luceneSearcher = new IndexSearcher(luceneReader);
		} 
		catch (IOException e) {
			log.fatal(String.format("Unable to load Lucene index reader. Error message: %s", e.getMessage()));
		}
	}
	
	public OdpDetails[] getOdpsByCategory(String category) {
		try {
			
			// Generate set of suitable ODP iris from category matching CSV file on disk
			Set<String> matchingOdpIris = new HashSet<String>();
			File odpCategoryMappingFile = new File(MetadataFetcher.class.getResource("odpCategoryMapping.csv").getFile());
			List<String> lines = Files.readAllLines(odpCategoryMappingFile.toPath());
			for (String line: lines) {
				String[] lineComponents = line.split(";");
				String lineCategory = lineComponents[0];
				String lineOdpIri = lineComponents[1];
				if (lineCategory.equalsIgnoreCase(category)) {
					matchingOdpIris.add(lineOdpIri);
				}
			}
			
			// 
			
			// Iterate over index and find documents with IRIs that are in the set of suitable ODPs
			List<OdpDetails> odps = new ArrayList<OdpDetails>();
			for (int i=0; i<luceneReader.maxDoc(); i++) {
				Document doc = luceneReader.document(i);
				String odpIri = doc.get("uri");
				// If category is "Any", return all ODPs. 
				// If not, only return if ODP IRI is in matching set of IRIs
				if (category.equalsIgnoreCase("Any") || matchingOdpIris.contains(odpIri)) {
					OdpDetails odp = new OdpDetails(doc.get("uri"),doc.get("name"));
					odps.add(odp);
				}
			}
			return odps.toArray(new OdpDetails[odps.size()]);
			
		}
		catch (Exception e) {
			log.error(String.format("Unable to retrieve ODP list for category %s: search failed with message: %s", category, e.getMessage()));
			return null;
		}
	}
	
	/**
	 * Enriches an OdpDetails object by looking up all fields in the Lucene index.
	 * @param odpUri URI of ODP to enrich.
	 * @return An OdpDetails object with all the fields that are stored in the index set.
	 */
	public OdpDetails getOdpDetails(String odpUri) {
		Analyzer analyzer = new WhitespaceAnalyzer();
		QueryParser queryParser = new QueryParser("uri", analyzer);

		// Search Lucene index to find ODP document 
		try {
			// Add quotes to search string before parsing to search for exact match.
			Query query = queryParser.parse(String.format("\"%s\"", odpUri));
			ScoreDoc[] hits = luceneSearcher.search(query, null, 1).scoreDocs;
			Document hit = luceneSearcher.doc(hits[0].doc);

			// All initial fields
			String odpName = null;
			String odpDescription = null;
			String[] odpDomains = null;
			String[] odpCqs = null;
			String odpImage = null;
			String[] odpScenarios = null;
			String[] odpClasses = null;
			String[] odpProperties = null;
			
			IndexableField nameField = hit.getField("name");
			if (nameField != null) {
				odpName = nameField.stringValue();
			}
			
			IndexableField descriptionField = hit.getField("description");
			if (descriptionField != null) {
				odpDescription = descriptionField.stringValue();
			}
			
			IndexableField[] cqFields = hit.getFields("cqs");
			odpCqs = new String[cqFields.length];
			for (int i=0; i<cqFields.length; i++) {
				odpCqs[i] = cqFields[i].stringValue();
			}
			
			IndexableField imageField = hit.getField("image");
			if (imageField != null) {
				odpImage = imageField.stringValue();
			}
			
			IndexableField[] classesFields = hit.getFields("classes");
			odpClasses = new String[classesFields.length];
			for (int i=0; i<classesFields.length; i++) {
				odpClasses[i] = classesFields[i].stringValue();
			}
			
			IndexableField[] propertiesFields = hit.getFields("properties");
			odpProperties = new String[propertiesFields.length];
			for (int i=0; i<propertiesFields.length; i++) {
				odpProperties[i] = propertiesFields[i].stringValue();
			}
			
			return new OdpDetails(odpUri,odpName, odpDescription, odpDomains, odpCqs, odpImage, odpScenarios, odpClasses, odpProperties);
		} 
		catch (Exception e) {
			log.error(String.format("Unable to enrich ODP %s: search failed with message: %s", odpUri, e.getMessage()));
			return null;
		}
	}

	public String[] getOdpCategories() {
		try {
			File odpCategoriesFile = new File(MetadataFetcher.class.getResource("odpCategories.txt").getFile());
			List<String> odpCategories = Files.readAllLines(odpCategoriesFile.toPath());
			return odpCategories.toArray(new String[odpCategories.size()]);
		}
		catch (Exception e) {
			return new String[]{String.format("Failed to get ODP categories. Exception thrown: ", e.toString())};
		}
	}
}