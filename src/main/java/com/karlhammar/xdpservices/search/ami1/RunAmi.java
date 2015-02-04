package com.karlhammar.xdpservices.search.ami1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.FSDirectory;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.item.Pointer;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.SearchResult;

class odpScoreEntry {
	public String odpFilename;
	public Double odpScore;
	
	public odpScoreEntry(String filename, Double score) {
		this.odpFilename = filename;
		this.odpScore = score;
	}
}

class odpScoreEntryComparator implements Comparator<odpScoreEntry> {
	@Override
	public int compare(odpScoreEntry o1, odpScoreEntry o2) {
		return o1.odpScore.compareTo(o2.odpScore);
	}
}

public class RunAmi {
	
	private static IDictionary wordnetDictionary;
	private static IndexReader luceneIndexReader;
	private static Set<String> stopwords;
	
	public static int LevenshteinDistance (String s0, String s1) {
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
	
	// Get all possible synonyms of some input string
	public static Set<String> getSynonyms(String inputTerm) {
		Set<String> synonyms = new HashSet<String>();
		IIndexWord idxWord = wordnetDictionary.getIndexWord(inputTerm, POS.NOUN);
		if (idxWord != null) {
			List<IWordID> wordIDs = idxWord.getWordIDs();
			for (IWordID wordID: wordIDs) {
	        	IWord word = wordnetDictionary.getWord(wordID);
	        	ISynset synset = word.getSynset();
	        	for (IWord w: synset.getWords()) {
	        		synonyms.add(w.getLemma());
	        	}
			}
		}
		return synonyms;
	}
	
	// Get all possible synonyms and hypernyms of some input term string
	// No semantic or other matching done, this is a widely thrown net..
	public static Set<String> getSynoAndHypernyms(String inputTerm) {
		Set<String> synoAndHypernyms = new HashSet<String>();
      	IIndexWord idxWord = wordnetDictionary.getIndexWord(inputTerm, POS.NOUN);
      	if (idxWord != null) {
      		List<IWordID> wordIDs = idxWord.getWordIDs();
      		for (IWordID wordID: wordIDs) {
	        	IWord word = wordnetDictionary.getWord(wordID);
	        	ISynset synset = word.getSynset();
	        	for (IWord w: synset.getWords()) {
	        		synoAndHypernyms.add(w.getLemma());
	        		//System.out.println("syn=" + w.getLemma());
	        	}
	        	List<ISynsetID> hypernyms = synset.getRelatedSynsets(Pointer.HYPERNYM);
	        	List<IWord> words;
	        	for (ISynsetID sid: hypernyms) {
	        		words = wordnetDictionary.getSynset(sid).getWords();
	        		for (Iterator<IWord> i = words.iterator(); i.hasNext();) {
	        			String next = i.next().getLemma();
	        			synoAndHypernyms.add(next);
	        			//System.out.println("hyper=" + next);
	        		}
	        	}
      		}
      	}
		return synoAndHypernyms;
	}
	
	// Matches query term synonyms and hypernyms against pattern term synonyms from Lucene index "synonyms" field.
	public static List<odpScoreEntry> SynonymOverlapSearch(String[] queryTerms) throws IOException {
		List<odpScoreEntry> resultsList = new ArrayList<odpScoreEntry>();
		Set<String> queryTermsExtended = new HashSet<String>();
		
		for (String queryTerm: queryTerms) {
			queryTermsExtended.addAll(getSynoAndHypernyms(queryTerm));
		}
		
		for (int i=0; i<luceneIndexReader.maxDoc(); i++) {
			Document doc = luceneIndexReader.document(i);
			IndexableField pathField = doc.getField("path");
			String suggestedOdpPath = pathField.stringValue();
			String suggestedOdpFilename = suggestedOdpPath.substring(suggestedOdpPath.lastIndexOf("/") + 1);
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
			odpScoreEntry entry = new odpScoreEntry(suggestedOdpFilename,matchScore);
			resultsList.add(entry);
		}
		
		// Reindex list to make compatible with 0-1 matching scale, and return
		return ReIndexTo10(resultsList);
		
	}
	
	// Reindex a list of ODP scores to range between 0 and 1.
	public static List<odpScoreEntry> ReIndexTo10(List<odpScoreEntry> inputList) {
		if (inputList.size() < 2) {
			return inputList;
		}
		else {
			// Find highest score in list. Not very efficient, could
			// probably just sort it and take last item instead, but too tired
			// to hack that now.
			Double highestScore = 0.0;
			for (odpScoreEntry entry: inputList) {
				if (entry.odpScore > highestScore) {
					highestScore = entry.odpScore;
				}
			}
			// TODO: Figure out why I'm dividing by two here?
			highestScore = highestScore / 2.0;
			// Reindex new list based on this score
			List<odpScoreEntry> outputList = new ArrayList<odpScoreEntry>();
			for (odpScoreEntry entry: inputList) {
				Double newScore = entry.odpScore / highestScore;
				odpScoreEntry newEntry = new odpScoreEntry(entry.odpFilename,newScore);
				outputList.add(newEntry);
			}
			return outputList;
		}
	}
	
	// This method compares WordNet synset ID:s in the query term set to those synset ID:s
	// known to be in the pattern set (held in the "sid" Lucene fields). This is a more refined
	// and hopefully more exact variation fo the SynonymOverlapSearch - whereas that search 
	// does not care about the semantics but rather matches ALL synonyms vs ALL synonyms and hypernyms,
	// this method makes use of the fact that class sense disambiguation has been performed at 
	// index time, hopefully narrowing the results to be more correct.
	public static List<odpScoreEntry> WordNetHypernymSearch(String[] queryTerms) throws IOException {
		List<odpScoreEntry> resultsList = new ArrayList<odpScoreEntry>();
		for (int i=0; i<luceneIndexReader.maxDoc(); i++) {
			Document doc = luceneIndexReader.document(i);
			IndexableField pathField = doc.getField("path");
			String suggestedOdpPath = pathField.stringValue();
			String suggestedOdpFilename = suggestedOdpPath.substring(suggestedOdpPath.lastIndexOf("/") + 1);
			
			// Get a list of SID:s associated with each query term (both synonyms and hypernyms)
			List<String> querySids = new ArrayList<String>();
			for (String queryTerm: queryTerms) {
				IIndexWord idxWord = wordnetDictionary.getIndexWord(queryTerm, POS.NOUN);
				if (idxWord != null) {
					List<IWordID> wordIds = idxWord.getWordIDs();
					for (IWordID wordId: wordIds) {
						IWord word = wordnetDictionary.getWord(wordId);
						ISynsetID synonymSid = word.getSynset().getID();
						querySids.add(synonymSid.toString());
						List<ISynsetID> parentSids = word.getSynset().getRelatedSynsets(Pointer.HYPERNYM);
						for (ISynsetID parentSid: parentSids) {
							querySids.add(parentSid.toString());
							List<ISynsetID> secondParentSids = wordnetDictionary.getSynset(parentSid).getRelatedSynsets(Pointer.HYPERNYM);
							for (ISynsetID secondParentSid: secondParentSids) {
								querySids.add(secondParentSid.toString());
								List<ISynsetID> thirdParentSids = wordnetDictionary.getSynset(secondParentSid).getRelatedSynsets(Pointer.HYPERNYM);
								for (ISynsetID thirdParentSid: thirdParentSids) {
									querySids.add(thirdParentSid.toString());
									List<ISynsetID> fourthParentSids = wordnetDictionary.getSynset(thirdParentSid).getRelatedSynsets(Pointer.HYPERNYM);
									for (ISynsetID fourthParentSid: fourthParentSids) {
										querySids.add(fourthParentSid.toString());
									}
								}
							}
						}
					}
				}
			}
			
			// Iterate over all sid fields (one per each Synonym ID found in the ODP at index time)
			// and fetch them to a list
			List<String> patternSids = new ArrayList<String>();
			IndexableField[] fields = doc.getFields("sid");
			for (IndexableField field: fields) {
				patternSids.add(field.stringValue());
			}
			
			// Compare the lists to get match score 			
			Double matchScore = 0.0;
			for (String querySid: querySids) {
				if (patternSids.contains(querySid)) {
					matchScore += Collections.frequency(patternSids, querySid);
				}	
			}
			
			// Add entry to results list
			odpScoreEntry entry = new odpScoreEntry(suggestedOdpFilename,matchScore);
			resultsList.add(entry);
		}
		// Reindex list to make compatible with 0-1 matching scale, and return
		return ReIndexTo10(resultsList);
	}

	// Do synonym and hypernym overlap matching over only the CQ:s
	public static List<odpScoreEntry> CQSynDistanceSearch(String inputCQ) throws IOException {
		List<odpScoreEntry> resultsList = new ArrayList<odpScoreEntry>();
		
		// Enrich input CQ with syno and hypernyms
		Set<String> inputTermsEnriched = new HashSet<String>();
		String[] inputCqAsStrings = prepareQueryString(inputCQ);
		
		for (String word: inputCqAsStrings) {
			inputTermsEnriched.addAll(getSynoAndHypernyms(word));
		}
		
		for (int i=0; i<luceneIndexReader.maxDoc(); i++) {
			Double matchScore = 0.0;
			Document doc = luceneIndexReader.document(i);
			IndexableField pathField = doc.getField("path");
			String suggestedOdpPath = pathField.stringValue();
			String suggestedOdpFilename = suggestedOdpPath.substring(suggestedOdpPath.lastIndexOf("/") + 1);
			
			List<String> patternTermsEnriched = new ArrayList<String>();
			IndexableField[] fields = doc.getFields("CQ");
			for (IndexableField field: fields) {
				String indexedCQ = field.stringValue();
				
				for (String word: prepareQueryString(indexedCQ)) {
					if (word.length() > 1) {
						patternTermsEnriched.addAll(getSynonyms(word));
					}
				}
			}
			
			for (String queryTerm: inputTermsEnriched) {
				if (patternTermsEnriched.contains(queryTerm)) {
					matchScore += Collections.frequency(patternTermsEnriched, queryTerm);
				}
			}
			odpScoreEntry entry = new odpScoreEntry(suggestedOdpFilename,matchScore);
			resultsList.add(entry);
		}
		return ReIndexTo10(resultsList);
	}
	
	// Ranks entries by the relative Levenshtein edit distance between pattern CQs and input CQ
	// Only shortest distance is used. 
	public static List<odpScoreEntry> CQEditDistanceSearch(String inputCQ) throws IOException {
		List<odpScoreEntry> resultsList = new ArrayList<odpScoreEntry>();
		for (int i=0; i<luceneIndexReader.maxDoc(); i++) {
			Document doc = luceneIndexReader.document(i);
			Double shortestDistance = 1.0;
			IndexableField pathField = doc.getField("path");
			String suggestedOdpPath = pathField.stringValue();
			String suggestedOdpFilename = suggestedOdpPath.substring(suggestedOdpPath.lastIndexOf("/") + 1);
			
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
			odpScoreEntry entry = new odpScoreEntry(suggestedOdpFilename,invertedDistance);
			resultsList.add(entry);
		}
		return resultsList;
	}
	
	// Execute Semantic Vectors Search (https://code.google.com/p/semanticvectors/)
	// Requires the existence of pregenerated term and document vectors named as per
	// the below configuration array.
	public static List<odpScoreEntry> SemanticVectorSearch(String[] queryTerms) {
		String[] configurationArray = {"-queryvectorfile","termvectors2.bin","-searchvectorfile", "docvectors2.bin","-searchtype","SUM","-numsearchresults", "25"};
		String[] queryArray = ArrayUtils.addAll(configurationArray, queryTerms);
		
		FlagConfig config = FlagConfig.getFlagConfig(queryArray);
		List<SearchResult> results = pitt.search.semanticvectors.Search.runSearch(config);
		List<odpScoreEntry> resultsList = new ArrayList<odpScoreEntry>();
		if (results.size() > 0) {
		      for (SearchResult result: results) {
		    	  String suggestedOdpPath = result.getObjectVector().getObject().toString();
		    	  String suggestedOdpFilename = suggestedOdpPath.substring(suggestedOdpPath.lastIndexOf("/") + 1);
		    	  Double suggestedOdpScore = result.getScore();
		    	  odpScoreEntry entry = new odpScoreEntry(suggestedOdpFilename, suggestedOdpScore);
		    	  resultsList.add(entry);
		      }
		}
		return resultsList;
	}
	
	// Prepare the string, removing question marks and such. Could be improved?	
	public static String[] prepareQueryString(String inputString) {
		inputString = inputString.toLowerCase();
		inputString = inputString.replace("?", "");
		inputString = inputString.replace("/", "");
		String[] splitInput = inputString.split(" ");
		return splitInput;
		// The below code does stop word removal on the input query. 
		// It has (strangely?) proven to lower recall across the dataset so is commented out.
		/*List<String> stopWordRemovedInput = new ArrayList<String>();
		for (String term: splitInput) {
			if (!stopwords.contains(term)) {
				stopWordRemovedInput.add(term);
			}
		}
		String[] returnArray = stopWordRemovedInput.toArray(new String[stopWordRemovedInput.size()]);
		return returnArray;*/
	}
	
	public static void main(String[] args) throws IOException {
		
		// prepare stop words  
		FileInputStream fis = new FileInputStream("/Users/karl/Dropbox/Forskning/PhD/Code/AMILucene/datasets/stopwords.txt");
		BufferedReader stopbr = new BufferedReader(new InputStreamReader(fis));
		stopwords = new HashSet<String>();
		String stopLine = null;
		while ((stopLine = stopbr.readLine()) != null) {
			stopwords.add(stopLine);
		}
		stopbr.close();
		
		// Open dataset and set up counters initially at zero
		BufferedReader br = new BufferedReader(new FileReader(new File("/Users/karl/Dropbox/Forskning/PhD/Code/AMILucene/datasets/ami.csv")));  
		String line = null;
		float totalImports = 0;
		float correctImports = 0;
		float numberOfQuestions = 0;
		
		// Prepare WordNet
		//String WnDictPath = System.getProperty("user.dir") + File.separator + "wordnet" + File.separator + "dict";
		String WnDictPath = "/Users/karl/Dropbox/Forskning/PhD/Code/AMILucene/wordnet/dict";
		URL url = new URL("file", null, WnDictPath);
		wordnetDictionary = new Dictionary(url);
		wordnetDictionary.open();
		
		// Prepare Lucene index
		luceneIndexReader = DirectoryReader.open(FSDirectory.open(new File("/Users/karl/Dropbox/Forskning/PhD/Code/AMILucene/index")));
		//luceneIndexReader = DirectoryReader.open(FSDirectory.open(new File("index")));
		
		// Variables that define whether answer patterns are independent or not
		// I.e. whether recall should be tabulated based on whether ALL patterns are matched
		// or just one per question (the latter being independent answers - any of them are good)
		// Independent datasets were constructed by the union of independent expert evaluations
		boolean firstLine = true;
		boolean independent = false;
		
		// Iterate over dataset
		while ((line = br.readLine()) != null)  
		{	
			if (firstLine) {
				if (line.indexOf("INDEPENDENT") != -1) {
					independent = true;
				}
				firstLine = false;
			}
			else {
				numberOfQuestions++;
				
				// Parse out data (CQ:s and correctly matched ODPs)
				String[] lineComponents = line.split(";");
				String competencyQuestion = lineComponents[0];
				System.out.println("CQ: " + competencyQuestion);
				Set<String> importedOdps = new HashSet<String>();
				for (int compCount = 1; compCount < lineComponents.length; compCount++) {
					String correctOdp = lineComponents[compCount];
					String correctOdpFilename = correctOdp.substring(correctOdp.lastIndexOf("/") + 1);
					System.out.println("Correct: " + correctOdpFilename);
					importedOdps.add(correctOdpFilename);
					totalImports++;
				}
				
				// Sanitize CQ for keyword based searches
				String[] queryTerms = prepareQueryString(competencyQuestion);
				
				// Execute the different search methods
				List<odpScoreEntry> SemanticVectorResults = SemanticVectorSearch(queryTerms);
				List<odpScoreEntry> CQEditDistanceResults = CQEditDistanceSearch(competencyQuestion);
				List<odpScoreEntry> SynonymOverlapResults = SynonymOverlapSearch(queryTerms);
				//List<odpScoreEntry> CQSynonymOverlapResults = CQSynDistanceSearch(competencyQuestion);
				//List<odpScoreEntry> WordNetHypernymResults = WordNetHypernymSearch(queryTerms);
				
				
				// Concatenate the generated result lists.
				List<odpScoreEntry> concatenatedResultsList = new ArrayList<odpScoreEntry>();
				concatenatedResultsList.addAll(SemanticVectorResults);
				concatenatedResultsList.addAll(CQEditDistanceResults);
				concatenatedResultsList.addAll(SynonymOverlapResults);
				//concatenatedResultsList.addAll(CQSynonymOverlapResults);
				//concatenatedResultsList.addAll(WordNetHypernymResults);
				
				// Merge scores using Map
				Map<String,Double> mergedResultsMap = new HashMap<String,Double>();
				for (odpScoreEntry entry: concatenatedResultsList) {
					if (!mergedResultsMap.containsKey(entry.odpFilename)) {
						mergedResultsMap.put(entry.odpFilename, entry.odpScore);
					}
					else {
						Double updatedScore = mergedResultsMap.get(entry.odpFilename) + entry.odpScore;
						mergedResultsMap.put(entry.odpFilename, updatedScore);
					}
				}
				
				// Turn said Map back into a list of odpScoreEntries
				List<odpScoreEntry> mergedResultsList = new ArrayList<odpScoreEntry>();
				for (Map.Entry<String, Double> entry : mergedResultsMap.entrySet()) {
				    String filename = entry.getKey();
				    Double score = entry.getValue();
				    odpScoreEntry newEntry = new odpScoreEntry(filename,score);
				    mergedResultsList.add(newEntry);
				}
				
				// Sort the merged list by scores, then reverse to get highest-scoring first
				Collections.sort(mergedResultsList, new odpScoreEntryComparator());
				Collections.reverse(mergedResultsList);
				
				// Iterate over result list, checking suggested ODPs against known correct matches
				for (int i = 0; i<25; i++) {
					if (mergedResultsList.size() <= i) {
						break;
					}
					odpScoreEntry entry = mergedResultsList.get(i);
					//System.out.println(entry.odpFilename + "; " + entry.odpScore);
					String suggestedOdpFilename = entry.odpFilename;
					if (importedOdps.contains(suggestedOdpFilename)) {
						correctImports++;
						System.out.println("Found match: " + suggestedOdpFilename);
						// In independent mode, if we get at least one hit which is in the 
						// dataset we have been successful and can break out of the loop.
						if (independent) {
							break;
						}
				    }
				}
			}
		}
		br.close();
		
		// Calculate and print recall
		System.out.println("Question count: " + numberOfQuestions);
		System.out.println("Correct guesses: " + correctImports);
		System.out.println("Total imports: " + totalImports);
		if (independent) {
			float recall = correctImports / numberOfQuestions;
			System.out.println("Recall (independent): " + recall);
		}
		else {
			float recall = correctImports / totalImports;
			System.out.println("Recall (non-independent): " + recall);
		}
	}
}
