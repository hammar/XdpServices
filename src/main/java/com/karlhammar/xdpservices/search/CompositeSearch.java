package com.karlhammar.xdpservices.search;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import com.karlhammar.xdpservices.data.CodpDetails;
import com.karlhammar.xdpservices.data.OdpSearchFilterConfiguration;
import com.karlhammar.xdpservices.data.OdpSearchResult;
import com.karlhammar.xdpservices.index.Indexer;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.SearchResult;

public class CompositeSearch {

	public final static CompositeSearch INSTANCE = new CompositeSearch();

	private static Log log;
	private static IndexReader luceneReader;
	private static IndexSearcher luceneSearcher;
	private static Boolean useLucene;
	private static Properties searchProperties;
	
	// Private constructor to defeat external instantiation (access via INSTANCE singleton)
	private CompositeSearch() {
		// Instantiate logging
		log = LogFactory.getLog(CompositeSearch.class);
		
		// Load search properties
		try {
			searchProperties = new Properties();
			searchProperties.load(CompositeSearch.class.getResourceAsStream("search.properties"));
		} 
		catch (IOException e) {
			log.fatal(String.format("Unable to load search properties. Error message: %s", e.getMessage()));
		}
		
		// Load Lucene reader
		try {
			Path luceneIndexPath = Paths.get(searchProperties.getProperty("luceneIndexPath"));
			luceneReader = DirectoryReader.open(FSDirectory.open(luceneIndexPath));
			luceneSearcher = new IndexSearcher(luceneReader);
			useLucene = true;
		} 
		catch (IOException e) {
			log.error(String.format("Unable to load Lucene index reader. Lucene support disabled. Error message: %s", e.getMessage()));
			useLucene = false;
		}
	}
	
	/**
	 * Execute Semantic Vectors Search (https://code.google.com/p/semanticvectors/).
	 * Requires the existence of term and document vectors named as per the (method-internal) 
	 * configuration array.
	 * Note that the OdpSearchResults returned by this method may contain incomplete ODPs, 
	 * e.g. need to be enriched in order to fill null fields.
	 * @param queryTerms Array of terms to search for.
	 * @return List of ODP search results with confidences.
	 */
	private static List<OdpSearchResult> SemanticVectorSearch(List<String> queryTerms) {
		try {
			String vectorBasePath = searchProperties.getProperty("semanticVectorsPath");
			String queryVectorPath = String.format("%s/termvectors.bin", vectorBasePath);
			String searchVectorPath = String.format("%s/docvectors.bin", vectorBasePath);
			//
			String[] configurationArray = {"-contentsfields","allterms","-docidfield", "iri","-queryvectorfile",queryVectorPath,"-searchvectorfile", searchVectorPath, "-searchtype", "SUM", "-numsearchresults", "25"};
			String[] queryTermsArray = queryTerms.toArray(new String[queryTerms.size()]);
			String[] queryArray = ArrayUtils.addAll(configurationArray, queryTermsArray);
			
			FlagConfig config = FlagConfig.getFlagConfig(queryArray);
			List<SearchResult> results = pitt.search.semanticvectors.Search.runSearch(config);
			List<OdpSearchResult> resultsList = new ArrayList<OdpSearchResult>();
			if (results.size() > 0) {
			      for (SearchResult result: results) {
			    	  String suggestedOdpPath = result.getObjectVector().getObject().toString();
			    	  Double suggestedOdpScore = result.getScore();
			    	  OdpSearchResult entry = new OdpSearchResult(new CodpDetails(suggestedOdpPath,""),suggestedOdpScore);
			    	  resultsList.add(entry);
			      }
			}
			return ReIndexTo10(resultsList);
		}
		catch (IllegalArgumentException ex) {
			// This happens if the incoming query terms, after filtering for junk,
			// aren't actually reasonable terms in the english language at all.
			// If so, return an empty result.
			return new ArrayList<OdpSearchResult>();
		}
	}
	
	
	/**
	 * Merges and sorts multiple result lists (e.g., coming from different search methods). 
	 * @param resultLists Arbitrary number of result lists.
	 * @return One merged/sorted result list.
	 */
	@SafeVarargs
	private static List<OdpSearchResult> mergeAndSortResults(List<OdpSearchResult>... resultLists) {
		
		// Concatenate the generated result lists.
		List<OdpSearchResult> concatenatedResultsList = new ArrayList<OdpSearchResult>();
		for (int i=0; i < resultLists.length; i++) {
			concatenatedResultsList.addAll(resultLists[i]);
		}

		
		// Merge scores using Map
		// Note that the map is string,double - since equality comparison of OdpDetails would be a nuisance, given
		// the many fields that this data type has.
		Map<String,Double> mergedResultsMap = new HashMap<String,Double>();
		for (OdpSearchResult entry: concatenatedResultsList) {
			if (!mergedResultsMap.containsKey(entry.getOdp().getIri())) {
				mergedResultsMap.put(entry.getOdp().getIri(), entry.getConfidence());
			}
			else {
				Double updatedScore = mergedResultsMap.get(entry.getOdp().getIri()) + entry.getConfidence();
				mergedResultsMap.put(entry.getOdp().getIri(), updatedScore);
			}
		}
		
		// Turn said Map back into a list of OdpSearchResult, creating new OdpDetails object with only
		// URI field set in the process.
		List<OdpSearchResult> mergedResultsList = new ArrayList<OdpSearchResult>();
		for (Map.Entry<String, Double> entry : mergedResultsMap.entrySet()) {
			CodpDetails odp = new CodpDetails(entry.getKey(), null);
		    Double confidence = entry.getValue();
		    OdpSearchResult newEntry = new OdpSearchResult(odp,confidence);
		    mergedResultsList.add(newEntry);
		}
		
		// Sort the merged list by scores, then reverse to get highest-scoring first
		mergedResultsList.sort(new Comparator<OdpSearchResult>() {
			@Override
			public int compare(OdpSearchResult osr1, OdpSearchResult osr2) {
				return osr1.getConfidence().compareTo(osr2.getConfidence());
			}
		});
		Collections.reverse(mergedResultsList);
		
		return mergedResultsList;
	}
	
	
	/**
	 * Enrich the metadata of ODPs in the input list of search results; search components may for efficiency reasons
	 * return search results where one or more ODP fields are null; this is where those fields are updated from
	 * the Lucene index.
	 * 
	 * Note: for the time being we only enrich by the name field. More may be added in future.
	 * 
	 * @param inputList List of ODP search results to be enriched.
	 * @return An enriched list with no ODPs having any null fields.
	 */
	private static List<OdpSearchResult> enrichResults(List<OdpSearchResult> inputList) {
		if (!useLucene) {
			// If Lucene inactivated, return immediately.
			return inputList;
		}
		else {
			// Set up stuff that will be needed
			List<OdpSearchResult> outputList = new ArrayList<OdpSearchResult>();
			Analyzer analyzer = new WhitespaceAnalyzer();
			QueryParser queryParser = new QueryParser("iri", analyzer);

			// Iterate over input list
			for (OdpSearchResult result: inputList) {

				// Get details for each result list entry
				String odpUri = result.getOdp().getIri().toString();
				Double confidence = result.getConfidence();

				// Search Lucene index to find ODP document 
				try {
					// Add quotes to search string before parsing to search for exact match.
					Query query = queryParser.parse(String.format("\"%s\"", odpUri));
					ScoreDoc[] hits = luceneSearcher.search(query, 1).scoreDocs;
					Document hit = luceneSearcher.doc(hits[0].doc);

					IndexableField nameField = hit.getField("name");
					String odpName = nameField.stringValue();
					OdpSearchResult newResult = new OdpSearchResult(new CodpDetails(odpUri,odpName), confidence);
					outputList.add(newResult);
				} 
				catch (Exception e) {
					log.error(String.format("Unable to enrich ODP %s: search failed with message: %s", odpUri, e.getMessage()));
					continue;
				}
			}
			return outputList;
		}
	}
	
	
	/**
	 * Filters such ODPs from the results set that are not consistent with the required search filter.
	 * @param inputList
	 * @param filterConfiguration
	 * @return
	 */
	private static List<OdpSearchResult> filterResults(List<OdpSearchResult> inputList, OdpSearchFilterConfiguration filterConfiguration) {
		// TODO: implement this.
		return inputList;
	}
	
	/**
	 * Reindex a list of ODP scores to range between 0 and 1.
	 * @param inputList
	 * @return
	 */
	private static List<OdpSearchResult> ReIndexTo10(List<OdpSearchResult> inputList) {
		if (inputList.size() < 2) {
			return inputList;
		}
		else {
			// Find highest score in list. Not very efficient, could
			// probably just sort it and take last item instead, but too tired
			// to hack that now.
			Double highestScore = 0.0;
			for (OdpSearchResult entry: inputList) {
				if (entry.getConfidence() > highestScore) {
					highestScore = entry.getConfidence();
				}
			}
			// Reindex new list based on this score
			List<OdpSearchResult> outputList = new ArrayList<OdpSearchResult>();
			for (OdpSearchResult entry: inputList) {
				Double newScore = entry.getConfidence() / highestScore;
				OdpSearchResult newEntry = new OdpSearchResult(entry.getOdp(),newScore);
				outputList.add(newEntry);
			}
			return outputList;
		}
	}
	
	/**
	 * Execute a query over all search engine methods.
	 * @param queryString The input query string.
	 * @param filterConfiguration Configuration of which results to exclude.
	 * @return List of ODP search results.
	 */
	public OdpSearchResult[] runSearch(String queryString, OdpSearchFilterConfiguration filterConfiguration) {
		
		// Prepare query for further processing
		String normalizedQueryString = queryString.toLowerCase().replace("?", "");
		
		// Tokenize query and remove stop words
		List<String> queryTerms = new ArrayList<String>();
		List<String> synonymTerms = new ArrayList<String>();
		try {
			Analyzer analyzer = new StandardAnalyzer();
	    	TokenStream tokenStream = analyzer.tokenStream(null,new StringReader(normalizedQueryString));
	    	tokenStream.reset();
	    	while(tokenStream.incrementToken()) {
	    		String term = tokenStream.getAttribute(CharTermAttribute.class).toString();
	    		queryTerms.add(term);
	    		synonymTerms.add(term);
	    		synonymTerms.addAll(Indexer.getSynonyms(term));
	    	}
	    	analyzer.close();
		}
		catch (IOException e) {
			log.error(String.format("Unable to tokenize input querystring. Error message: %s", e.getMessage()));
		}
		
		// Execute searches across all search engine methods
		List<OdpSearchResult> SemanticVectorResults = SemanticVectorSearch(queryTerms);
		List<OdpSearchResult> LuceneResults = LuceneSearch(normalizedQueryString);
		// Deactivated due to poor results
		// List<OdpSearchResult> SynonymSearchResults = SynonymSearch(normalizedQueryString);
		
		// Merge, enrich, and filter results
		List<OdpSearchResult> mergedResults = mergeAndSortResults(SemanticVectorResults,LuceneResults);
		List<OdpSearchResult> enrichedResults = enrichResults(ReIndexTo10(mergedResults));
		List<OdpSearchResult> filteredResults = filterResults(enrichedResults, filterConfiguration);
		
		OdpSearchResult[] resultsArray = filteredResults.toArray(new OdpSearchResult[filteredResults.size()]);
		return resultsArray;
	}

	/**
	 * Executes a standard Lucene query using the WhiteSpace-analyser over the synonyms field
	 * (e.g., no fancy language-specific grammars or stemming or stop word removal, simply compare
	 * the query terms to all terms in the ODPs)
	 * @param queryString
	 * @return
	 */
	@SuppressWarnings("unused")
	private List<OdpSearchResult> SynonymSearch(String queryString) {
		List<OdpSearchResult> resultsList = new ArrayList<OdpSearchResult>();
		if (!useLucene) {
			return resultsList;
		}
		else {
			try {
				WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
				Query q = new QueryParser("synonyms", analyzer).parse(queryString);
				TopDocs docs = luceneSearcher.search(q, 25);
				ScoreDoc[] hits = docs.scoreDocs;
				for (int i=0; i<hits.length; ++i) {
					ScoreDoc sdoc = hits[i];
				    int docId = sdoc.doc;
				    float score = sdoc.score;
				    Document doc = luceneSearcher.doc(docId);
				    OdpSearchResult entry = new OdpSearchResult(new CodpDetails(doc.getField("iri").stringValue(),doc.getField("name").toString()), new Double(score));
					resultsList.add(entry);
				}
			} 
			catch (Exception e) {
				log.error(String.format("Unable to execute Lucene synonym search with WhiteSpace analyzer. Error message: %s", e.getMessage()));	
			}
		}
		// Reindex list to make compatible with 0-1 matching scale, and return
		return ReIndexTo10(resultsList);
	}
	
	
	/**
	 * Executes a standard Lucene query using the WhiteSpace-analyser over the allterms-field
	 * (e.g., no fancy language-specific grammars or stemming or stop word removal, simply compare
	 * the query terms to all terms in the ODPs)
	 * @param queryString
	 * @return
	 */
	private List<OdpSearchResult> LuceneSearch(String queryString) {
		List<OdpSearchResult> resultsList = new ArrayList<OdpSearchResult>();
		if (!useLucene) {
			return resultsList;
		}
		else {
			try {
				WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
				Query q = new QueryParser("allterms", analyzer).parse(queryString);
				TopDocs docs = luceneSearcher.search(q, 25);
				ScoreDoc[] hits = docs.scoreDocs;
				for (int i=0; i<hits.length; ++i) {
					ScoreDoc sdoc = hits[i];
				    int docId = sdoc.doc;
				    float score = sdoc.score;
				    Document doc = luceneSearcher.doc(docId);
				    OdpSearchResult entry = new OdpSearchResult(new CodpDetails(doc.getField("iri").stringValue(),doc.getField("name").stringValue()), new Double(score));
					resultsList.add(entry);
				}
			} 
			catch (Exception e) {
				log.error(String.format("Unable to execute Lucene search with WhiteSpace analyzer. Error message: %s", e.getMessage()));	
			}
		}
		// Reindex list to make compatible with 0-1 matching scale, and return
		return ReIndexTo10(resultsList);
	}
}