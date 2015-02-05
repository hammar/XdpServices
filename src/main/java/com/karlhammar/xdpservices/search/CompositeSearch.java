package com.karlhammar.xdpservices.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.FSDirectory;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.SearchResult;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.item.Pointer;
import edu.stanford.bmir.protege.web.shared.xd.OdpDetails;
import edu.stanford.bmir.protege.web.shared.xd.OdpSearchFilterConfiguration;
import edu.stanford.bmir.protege.web.shared.xd.OdpSearchResult;
import edu.stanford.bmir.protege.web.shared.xd.OdpSearchResultComparator;

public class CompositeSearch {

	public final static CompositeSearch INSTANCE = new CompositeSearch();

	private static Log log;
	private static Set<String> stopwords;
	private static IDictionary wordnetDictionary;
	private static Boolean useWordNet; 
	private static IndexReader luceneReader;
	private static Boolean useLucene;
	private static Properties searchProperties;
	
	// Private constructor to defeat external instantiation (access via INSTANCE singleton)
	private CompositeSearch() {
		log = LogFactory.getLog(CompositeSearch.class);
		loadSearchProperties();
		stopwords = loadStopWords();
		loadWordNetDictionary();
		loadLuceneReader();
	}
	
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
			String luceneIndexPath = searchProperties.getProperty("luceneIndexPath");
			luceneReader = DirectoryReader.open(FSDirectory.open(new File(luceneIndexPath)));
			useLucene = true;
		} 
		catch (IOException e) {
			log.error(String.format("Unable to load Lucene index reader. Lucene support disabled. Error message: %s", e.getMessage()));
			useLucene = false;
		}
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
			log.error(String.format("Unable to load WordNet. WordNet-based query synonym/hypernym enrichment disabled. Error message: %s", ex.getMessage()));
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
	 * Normalize input query string, removing control characters and turning it into an array of
	 * lower case words.
	 * @param inputString The input query string.
	 * @return An array of terms of the normalized query string.
	 */
	private static String[] prepareQueryString(String inputString) {
		inputString = inputString.toLowerCase();
		inputString = inputString.replace("?", "");
		inputString = inputString.replace("/", "");
		String[] splitInput = inputString.split(" ");
		return splitInput;
		// The below code does stop word removal on the input query. 
		// It has (strangely?) proven to lower recall across the dataset so is commented out for now.
		/*List<String> stopWordRemovedInput = new ArrayList<String>();
		for (String term: splitInput) {
			if (!stopwords.contains(term)) {
				stopWordRemovedInput.add(term);
			}
		}
		String[] returnArray = stopWordRemovedInput.toArray(new String[stopWordRemovedInput.size()]);
		return returnArray;*/
	}
	
	
	
	//
	/**
	 * Execute Semantic Vectors Search (https://code.google.com/p/semanticvectors/).
	 * Requires the existence of term and document vectors named as per the (method-internal) 
	 * configuration array.
	 * Note that the OdpSearchResults returned by this method may contain incomplete ODPs, 
	 * e.g. need to be enriched in order to fill null fields.
	 * @param queryTerms Array of terms to search for.
	 * @return List of ODP search results with confidences.
	 */
	private static List<OdpSearchResult> SemanticVectorSearch(Set<String> queryTerms) {
		String vectorBasePath = searchProperties.getProperty("semanticVectorsPath");
		String queryVectorPath = String.format("%s/termvectors2.bin", vectorBasePath);
		String searchVectorPath = String.format("%s/docvectors2.bin", vectorBasePath);
		String[] configurationArray = {"-queryvectorfile",queryVectorPath,"-searchvectorfile", searchVectorPath,"-docidfield", "uri", "-searchtype", "SUM", "-numsearchresults", "25"};
		String[] queryTermsArray = queryTerms.toArray(new String[queryTerms.size()]);
		String[] queryArray = ArrayUtils.addAll(configurationArray, queryTermsArray);
		
		FlagConfig config = FlagConfig.getFlagConfig(queryArray);
		List<SearchResult> results = pitt.search.semanticvectors.Search.runSearch(config);
		List<OdpSearchResult> resultsList = new ArrayList<OdpSearchResult>();
		if (results.size() > 0) {
		      for (SearchResult result: results) {
		    	  String suggestedOdpPath = result.getObjectVector().getObject().toString();
		    	  Double suggestedOdpScore = result.getScore();
		    	  OdpSearchResult entry = new OdpSearchResult(new OdpDetails(suggestedOdpPath),suggestedOdpScore);
		    	  resultsList.add(entry);
		      }
		}
		return resultsList;
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
			if (!mergedResultsMap.containsKey(entry.getOdp().getUri())) {
				mergedResultsMap.put(entry.getOdp().getUri(), entry.getConfidence());
			}
			else {
				Double updatedScore = mergedResultsMap.get(entry.getOdp().getUri()) + entry.getConfidence();
				mergedResultsMap.put(entry.getOdp().getUri(), updatedScore);
			}
		}
		
		// Turn said Map back into a list of OdpSearchResult, creating new OdpDetails object with only
		// URI field set in the process.
		List<OdpSearchResult> mergedResultsList = new ArrayList<OdpSearchResult>();
		for (Map.Entry<String, Double> entry : mergedResultsMap.entrySet()) {
			OdpDetails odp = new OdpDetails(entry.getKey());
		    Double confidence = entry.getValue();
		    OdpSearchResult newEntry = new OdpSearchResult(odp,confidence);
		    mergedResultsList.add(newEntry);
		}
		
		// Sort the merged list by scores, then reverse to get highest-scoring first
		Collections.sort(mergedResultsList, new OdpSearchResultComparator());
		Collections.reverse(mergedResultsList);
		
		return mergedResultsList;
	}
	
	
	/**
	 * Enrich the metadata of ODPs in the input list of search results; search components may for efficiency reasons
	 * return search results where one or more ODP fields are null; this is where those fields are updated from
	 * the ODP metadata repository.
	 * @param inputList List of ODP search results to be enriched.
	 * @return An enriched list with no ODPs having any null fields.
	 */
	private static List<OdpSearchResult> enrichResults(List<OdpSearchResult> inputList) {
		// TODO: Implement this
		return inputList;
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
	 * Get all possible synonyms and hypernyms of some input term string from WordNet.
	 * No semantic or other matching done, this is a widely thrown net..
	 * @param inputTerm
	 * @return
	 */
	private static Set<String> getSynoAndHypernyms(String inputTerm) {
		Set<String> synoAndHypernyms = new HashSet<String>();
		
		if (!useWordNet) {
			return synoAndHypernyms;
		}
		else {
	      	IIndexWord idxWord = wordnetDictionary.getIndexWord(inputTerm, POS.NOUN);
	      	if (idxWord != null) {
	      		List<IWordID> wordIDs = idxWord.getWordIDs();
	      		for (IWordID wordID: wordIDs) {
		        	IWord word = wordnetDictionary.getWord(wordID);
		        	ISynset synset = word.getSynset();
		        	for (IWord w: synset.getWords()) {
		        		synoAndHypernyms.add(w.getLemma());
		        	}
		        	List<ISynsetID> hypernyms = synset.getRelatedSynsets(Pointer.HYPERNYM);
		        	List<IWord> words;
		        	for (ISynsetID sid: hypernyms) {
		        		words = wordnetDictionary.getSynset(sid).getWords();
		        		for (Iterator<IWord> i = words.iterator(); i.hasNext();) {
		        			String next = i.next().getLemma();
		        			synoAndHypernyms.add(next);
		        		}
		        	}
	      		}
	      	}
			return synoAndHypernyms;
		}
	}
	 
	/**
	 * Ranks entries by the relative Levenshtein edit distance between pattern CQs and input CQ.
	 * Only shortest distance is used.
	 * @param inputCQ
	 * @return
	 * @throws IOException
	 */
	private static List<OdpSearchResult> CQEditDistanceSearch(String inputCQ) {
			List<OdpSearchResult> resultsList = new ArrayList<OdpSearchResult>();
			if (!useLucene) {
				return resultsList;
			}
			else {
				for (int i=0; i<luceneReader.maxDoc(); i++) {
					try {
						Document doc = luceneReader.document(i);
						Double shortestDistance = 1.0;
						IndexableField uriField = doc.getField("uri");
						String suggestedOdpUri = uriField.stringValue();
						
						
						// Iterate over all CQ fields (there may be more than one if more than one CQ existed
						// for this document when indexing). We only keep the shortest edit distance.
						IndexableField[] fields = doc.getFields("CQ");
						for (IndexableField field: fields) {
							String indexedCQ = field.stringValue();
							// Get maximum possible Levenshtein distance, used to calculate relative distance
							Double max_distance;
							if (inputCQ.length() > indexedCQ.length()) {
								max_distance = new Double(inputCQ.length());
							}
							else {
								max_distance = new Double(indexedCQ.length());
							}
							// Calculate relative string edit distance here, and keep only the shortest one
							// for any document in index.
							Double absolute_distance = new Double(LevenshteinDistance(indexedCQ, inputCQ));
							Double relative_distance = absolute_distance/max_distance; 
							if (relative_distance < shortestDistance) {
								shortestDistance = relative_distance;
							}
						}
						// Invert edit distance to get similarity score
						Double invertedDistance = 1.0 - shortestDistance;
						
						// Create score entry and add to results list
						OdpSearchResult entry = new OdpSearchResult(new OdpDetails(suggestedOdpUri),invertedDistance);
						resultsList.add(entry);
					}
					catch (IOException ex) {
						log.error(String.format("Unable to retrieve document %s from Lucene index. Error message: %s", i, ex.getMessage()));
					}
				}
				return resultsList;
			}
		}
	
	
	
	/**
	 * Matches query term synonyms and hypernyms against pattern term synonyms from Lucene index "synonyms" field.
	 * @param queryTerms
	 * @return
	 * @throws IOException
	 */
	private static List<OdpSearchResult> SynonymOverlapSearch(Set<String> queryTermsExtended) {
		List<OdpSearchResult> resultsList = new ArrayList<OdpSearchResult>();

		if (!useLucene) {
			return resultsList;
		}
		else {
			for (int i=0; i<luceneReader.maxDoc(); i++) {
				try {
					Document doc = luceneReader.document(i);
					IndexableField uriField = doc.getField("uri");
					String suggestedOdpUri = uriField.stringValue();
					
					IndexableField field = doc.getField("synonyms");
					Double matchScore = 0.0;
					if (field != null) {
						String synonymString = field.stringValue();
						List<String> synonyms = new ArrayList<String>(Arrays.asList(synonymString.split(" ")));
						for (String queryTerm: queryTermsExtended) {
							if (synonyms.contains(queryTerm)) {
								matchScore += Collections.frequency(synonyms, queryTerm);
							}
						}
					}
					OdpSearchResult entry = new OdpSearchResult(new OdpDetails(suggestedOdpUri),matchScore);
					resultsList.add(entry);
				}
				catch (IOException ex) {
					log.error(String.format("Unable to retrieve document %s from Lucene index. Error message: %s", i, ex.getMessage()));
				}
			}
			// Reindex list to make compatible with 0-1 matching scale, and return
			return ReIndexTo10(resultsList);
		}
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
			// TODO: Figure out why I'm dividing by two here?
			highestScore = highestScore / 2.0;
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
	 * Compute Levenshtein distance between two strings.
	 * @param s0 String 1
	 * @param s1 String 2
	 * @return The distance.
	 */
	private static int LevenshteinDistance (String s0, String s1) {
		int len0 = s0.length()+1;
		int len1 = s1.length()+1;
		// the array of distances
		int[] cost = new int[len0];
		int[] newcost = new int[len0];
		// initial cost of skipping prefix in String s0
		for(int i=0;i<len0;i++) cost[i]=i;
		// dynamicaly computing the array of distances
		// transformation cost for each letter in s1
		for(int j=1;j<len1;j++) {
			// initial cost of skipping prefix in String s1
			newcost[0]=j-1;
			// transformation cost for each letter in s0
			for(int i=1;i<len0;i++) {
				// matching current letters in both strings
				int match = (s0.charAt(i-1)==s1.charAt(j-1))?0:1;
				// computing cost for each transformation
				int cost_replace = cost[i-1]+match;
				int cost_insert  = cost[i]+1;
				int cost_delete  = newcost[i-1]+1;
				// keep minimum cost
				newcost[i] = Math.min(Math.min(cost_insert, cost_delete),cost_replace );
			}
			// swap cost/newcost arrays
			int[] swap=cost; cost=newcost; newcost=swap;
		}
		// the distance is the cost for transforming all letters in both strings
		return cost[len0-1];
	}
	
	
	
	/**
	 * Execute a query over all search engine methods.
	 * @param queryString The input query string.
	 * @param filterConfiguration Configuration of which results to exclude.
	 * @return List of ODP search results.
	 */
	public List<OdpSearchResult> runSearch(String queryString, OdpSearchFilterConfiguration filterConfiguration) {
		
		// Normalise query into a term array
		String[] queryTerms = prepareQueryString(queryString);
		
		// Enrich those terms (using WordNet). Note use of set to avoid duplicates that could color
		// results based on skew in WordNet synonym distributions (or natural language).
		Set<String> inputTermsEnriched = new HashSet<String>();
		for (String word: queryTerms) {
			inputTermsEnriched.addAll(getSynoAndHypernyms(word));
		}
		
		// Execute searches across all search engine methods
		List<OdpSearchResult> SemanticVectorResults = SemanticVectorSearch(inputTermsEnriched);
		List<OdpSearchResult> SynonymOverlapResults = SynonymOverlapSearch(inputTermsEnriched);
		List<OdpSearchResult> CQEditDistanceResults = CQEditDistanceSearch(queryString);
		
		List<OdpSearchResult> mergedResults = mergeAndSortResults(SemanticVectorResults,SynonymOverlapResults,CQEditDistanceResults);
		List<OdpSearchResult> enrichedResults = enrichResults(mergedResults);
		List<OdpSearchResult> filteredResults = filterResults(enrichedResults, filterConfiguration);
		return filteredResults;
	}
}