package com.karlhammar.xdpservices.retrieve;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
	 * @throws IOException 
	 */
	public CodpDetails[] getOdpsByCategory(String category) throws IOException {
		List<CodpDetails> odps = new ArrayList<CodpDetails>();
		// Iterate over all documents in index
		for (int i=0; i<luceneReader.maxDoc(); i++) {
			// Flag for whether this document is a hit or not
			boolean odpMatchesCategory = false;
			Document doc = luceneReader.document(i);
			// Iterate over all instances of the "domain" field
			IndexableField[] domainFields = doc.getFields("domain");
			for (int ii=0; ii<domainFields.length; ii++) {
				// If there is a match, set the flag
				if (domainFields[ii].stringValue().trim().equalsIgnoreCase(category)) {
					odpMatchesCategory = true;
				}
			}
			// If the document is flagged, create a CodpDetails object and add to return list 
			if (odpMatchesCategory || category.equalsIgnoreCase("Any")) {
				CodpDetails odp = new CodpDetails(doc.get("iri"), doc.get("name"));
				odps.add(odp);
			}
		}
		// Sort, transform into an array and return
		odps.sort(new Comparator<CodpDetails>() {
			@Override
			public int compare(CodpDetails o1, CodpDetails o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		return odps.toArray(new CodpDetails[odps.size()]);
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
			ScoreDoc[] hits = luceneSearcher.search(query, 1).scoreDocs;
			Document hit = luceneSearcher.doc(hits[0].doc);
			
			// All initial fields
			String odpName = null;
			String odpImage = null;
			String odpIntent = null;
			String odpDescription = null;
			String odpConsequences = null;
			List<String> odpDomains = new ArrayList<String>();
			List<String> odpScenarios = new ArrayList<String>();
			List<String> odpCqs = new ArrayList<String>();

			// Get each field from Lucene index
			IndexableField nameField = hit.getField("name");
			odpName = nameField.stringValue();
			
			IndexableField imageField = hit.getField("image");
			if (imageField != null) {
				odpImage = imageField.stringValue();
			}
			
			IndexableField intentField = hit.getField("intent");
			if (intentField != null) {
				odpIntent = intentField.stringValue();
			}
			
			IndexableField descriptionField = hit.getField("description");
			if (descriptionField != null) {
				odpDescription = descriptionField.stringValue();
			}
			
			IndexableField consequencesField = hit.getField("consequences");
			if (consequencesField != null) {
				odpConsequences = consequencesField.stringValue();
			}
			
			IndexableField[] domainFields = hit.getFields("domain");
			for (int i=0; i<domainFields.length; i++) {
				String domain = domainFields[i].stringValue();
				odpDomains.add(domain);
			}
			
			IndexableField[] scenarioFields = hit.getFields("scenario");
			for (int i=0; i<scenarioFields.length; i++) {
				String scenario = scenarioFields[i].stringValue();
				odpScenarios.add(scenario);
			}
			
			IndexableField[] cqFields = hit.getFields("cq");
			for (int i=0; i<cqFields.length; i++) {
				String cq = cqFields[i].stringValue();
				odpCqs.add(cq);
			}
			
			// Create and return new CodpDetails object
			return new CodpDetails(odpIri,odpName, odpImage, odpIntent, odpDescription, odpConsequences,
					odpDomains, odpScenarios, odpCqs); 
		} 
		catch (Exception e) {
			log.error(String.format("Unable to enrich ODP %s: search failed with message: %s", odpIri, e.getMessage()));
			return null;
		}
		
	}
	
	/**
	 * Returns a string array of all ODP categories (i.e., unique values for the string 
	 * field "domain") present in the Lucene index, sorted alphabetically, with the
	 * additional value "Any" in first place.
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
		odpCategoriesAsList.add(0, "Any");
		// Transform into an array and return
		return odpCategoriesAsList.toArray(new String[odpCategoriesAsList.size()]);
	}
}
