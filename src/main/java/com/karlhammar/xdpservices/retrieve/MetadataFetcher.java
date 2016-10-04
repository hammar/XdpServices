package com.karlhammar.xdpservices.retrieve;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;
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
import com.karlhammar.xdpservices.data.CodpDetails;
import com.karlhammar.xdpservices.search.CompositeSearch;

//import edu.stanford.bmir.protege.web.shared.xd.OdpDetails;

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
		// Instantiate logging
		log = LogFactory.getLog(MetadataFetcher.class);
		
		// Get search configuration
		try {
			searchProperties = new Properties();
			searchProperties.load(CompositeSearch.class.getResourceAsStream("search.properties"));
		} 
		catch (IOException e) {
			log.fatal(String.format("Unable to load search properties. Error message: %s", e.getMessage()));
		}

		// Load Lucene reader and searcher
		try {
			Path luceneIndexPath = Paths.get(searchProperties.getProperty("luceneIndexPath"));
			luceneReader = DirectoryReader.open(FSDirectory.open(luceneIndexPath));
			luceneSearcher = new IndexSearcher(luceneReader);
		} 
		catch (IOException e) {
			log.fatal(String.format("Unable to load Lucene index reader. Error message: %s", e.getMessage()));
		}
	}

	/**
	 * Return an array of CodpDetails objects that have the input category set as value for
	 * the "domain" string field in the Lucene index.
	 * @param category ODP category to search for.
	 * @return
	 */
	public CodpDetails[] getOdpsByCategory(String category) {
		return null;
		// TODO: Reimplement this with new Lucene index structure
		/*try {

			// Generate set of suitable ODP iris from category matching CSV file on disk
			Set<String> matchingOdpIris = new HashSet<String>();
			final List<String> resourceLines = IOUtils.readLines(MetadataFetcher.class.getResourceAsStream("odpCategoryMapping.csv"));
			for (final String line : resourceLines) {
				String[] lineComponents = line.split(";");
				String lineCategory = lineComponents[0];
				String lineOdpIri = lineComponents[1];
				if (lineCategory.equalsIgnoreCase(category)) {
					matchingOdpIris.add(lineOdpIri);
				}
			}
			
			// 
			
			// Iterate over index and find documents with IRIs that are in the set of suitable ODPs
			List<CodpDetails> odps = new ArrayList<CodpDetails>();
			for (int i=0; i<luceneReader.maxDoc(); i++) {
				Document doc = luceneReader.document(i);
				String odpIri = doc.get("uri");
				// If category is "Any", return all ODPs. 
				// If not, only return if ODP IRI is in matching set of IRIs
				if (category.equalsIgnoreCase("Any") || matchingOdpIris.contains(odpIri)) {
					CodpDetails odp = new CodpDetails(doc.get("uri"),doc.get("name"));
					odps.add(odp);
				}
			}
			return odps.toArray(new CodpDetails[odps.size()]);
			
		}
		catch (Exception e) {
			log.error(String.format("Unable to retrieve ODP list for category %s: search failed with message: %s", category, e.getMessage()));
			e.printStackTrace();
			return null;
		}*/
	}
	
	/**
	 * Retrieve a CodpDetails object by looking up all fields in the Lucene index, based on
	 * an input IRI.
	 * @param odpIri IRI of the ODP to fetch
	 * @return A CodpDetails object with all the fields that are stored in the index set.
	 */
	public CodpDetails getOdpDetails(String odpIri) {
		Analyzer analyzer = new WhitespaceAnalyzer();
		QueryParser queryParser = new QueryParser("iri", analyzer);

		// Search Lucene index to find ODP document 
		try {
			// Add quotes to search string before parsing to search for exact match.
			Query query = queryParser.parse(String.format("\"%s\"", odpIri));
			ScoreDoc[] hits = luceneSearcher.search(query, null, 1).scoreDocs;
			Document hit = luceneSearcher.doc(hits[0].doc);

			// TODO: Implement the below based on new Lucene index structure
			return null;
			/*
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
				odpDescription = descriptionField.stringValue().trim().replace("\n\n\n\n", "\n\n");
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
			else {
				odpImage = getOdpImageFromCsvLookup(odpIri);
			}
			
			IndexableField[] scenarioFields = hit.getFields("scenario");
			odpScenarios = new String[scenarioFields.length];
			for (int i=0; i<scenarioFields.length; i++) {
				odpScenarios[i] = scenarioFields[i].stringValue();
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
			return new CodpDetails(odpIri,odpName, odpImage, odpDescription, odpDescription, odpDescription,
					Arrays.asList(odpScenarios),Arrays.asList(odpScenarios), Arrays.asList(odpCqs)); 
					
				*/	
		} 
		catch (Exception e) {
			log.error(String.format("Unable to enrich ODP %s: search failed with message: %s", odpIri, e.getMessage()));
			return null;
		}
		
	}
	/*
	private String getOdpImageFromCsvLookup(String odpIri) {
		try {
			final List<String> resourceLines = IOUtils.readLines(MetadataFetcher.class.getResourceAsStream("odpIllustrationMapping.csv"));
			for (final String line : resourceLines) {
				String[] lineComponents = line.split(";");
				String lineIri = lineComponents[0];
				String lineIllustrationIri = lineComponents[1];
				if (lineIri.equalsIgnoreCase(odpIri)) {
					return lineIllustrationIri;
				}
			}
			return null;
		}
		catch (Exception ex) {
			return null;
		}
	}*/
	
	/**
	 * Returns a string array of all ODP categories (i.e., unique values for the string 
	 * field "domain") present in the Lucene index, sorted alphabetically.
	 * @return
	 * @throws IOException
	 */
	public String[] getOdpCategories() throws IOException {
		Set<String> odpCategories = new HashSet<String>();
		// Iterate over all documents in index
		for (int i=0; i<luceneReader.maxDoc(); i++) {
			Document doc = luceneReader.document(i);
			// Iterate over all instances of the "domain" field
			IndexableField[] domainFields = doc.getFields("domain");
			for (int ii=0; ii<domainFields.length; ii++) {
				String category = domainFields[ii].stringValue().trim();
				// If field value is non-empty, add it
				if (!category.equalsIgnoreCase("")) {
					odpCategories.add(category);
				}
			}
		}
		// Transform set into list and sort
		List<String> odpCategoriesAsList = new ArrayList<String>();
		odpCategoriesAsList.addAll(odpCategories);
		Collections.sort(odpCategoriesAsList);
		// Transform into an array and return
		return odpCategoriesAsList.toArray(new String[odpCategoriesAsList.size()]);
	}
}
