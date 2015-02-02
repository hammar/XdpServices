package com.karlhammar.xdpservices.search.ami1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import com.google.common.base.CaseFormat;

import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.item.Pointer;
import edu.mit.jwi.morph.WordnetStemmer;

public class WordnetSynsetMatcher {
	
	private static Set<String> stopwords;
	private static IDictionary dict;
	private static WordnetStemmer stemmer;
	
	public static ISynsetID suggestWordnetSynsetId(OWLClass inputClass, OWLOntology ontology, IDictionary wordnetDictionary, Set<String> inputStopwords) {
		
		// We are only interested in classes that are non-anonymous and not owl:thing or owl:nothing
		if (inputClass.isAnonymous() || inputClass.isOWLThing() || inputClass.isOWLNothing()) {
			return null;
		}
		
		System.out.println("suggesting sid for: " + inputClass.toString());
		
		// Set up infrastructure
		stopwords = inputStopwords;
		dict = wordnetDictionary;
		stemmer = new WordnetStemmer(dict);
		OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();
		OWLAnnotationProperty labelProperty = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
		Set<OWLAnnotation> labelAnnotations = inputClass.getAnnotations(ontology, labelProperty);
		
		//System.out.println(labelAnnotations.toString());
		
		// Set up a set of all labels for this class
		Set<String> labels = new HashSet<String>();
		
		// Get all labels that are in english or lack language definition
		// Stop words are removed and other words are normalized using the private
		// method for that purpose.
		for (OWLAnnotation labelAnnotation: labelAnnotations) {
			if (labelAnnotation.getValue() instanceof OWLLiteral) {
				OWLLiteral labelAnnotationAsLiteral = (OWLLiteral)labelAnnotation.getValue();
				//System.out.println(labelAnnotationAsLiteral.toString());
				String labelLang = labelAnnotationAsLiteral.getLang();
				//System.out.println(labelLang);
				if (labelLang.equalsIgnoreCase("") || labelLang.equalsIgnoreCase("en")) {
					
					String label = labelAnnotationAsLiteral.getLiteral();
					//System.out.println(label);
					String normalizedLabel = getNormalizedWord(label);
					if (!isStopOrNonexistantWord(normalizedLabel)) {
						//System.out.println(normalizedLabel);
						labels.add(normalizedLabel);
					}
				}
			}
		}
		//System.out.println(labels.toString());
		
		// If no suitable labels exist for the class, try to generate one the class URI instead.
		if (labels.size() < 1) {
			String candidateLabel = getLabelFromUri(inputClass, ontology);
			String normalizedLabel = getNormalizedWord(candidateLabel);
			if (!isStopOrNonexistantWord(normalizedLabel)) {
				labels.add(normalizedLabel);
			}
		}

		// If there are multiple labels, find and return shared or most common joint synset
		if (labels.size() > 1) {
			ISynsetID classSynset = getMostCommonSynset(labels);
			return(classSynset);
		}
		
		// Finally, if there is only one label, use it to figure out which synset(s) is the
		// corresponding one
		else if (labels.size() == 1){
			String label = labels.iterator().next();
			Set<ISynsetID> synsets = getAllSynsetsForLabel(label);
			
			// If no candidate synsets can be found (i.e. misspellings), return null.
			// This should never happen, as the isStopOrNonexistantWord method checks 
			// that all labels are actually in WordNet.
			if (synsets.size() < 1) {
				return null;
			}
			
			// If there is only one candidate synset, return it straight away
			if (synsets.size() == 1) {
				ISynsetID classSynset = synsets.iterator().next();
				return classSynset;
			}
			
			// This is likely to be the most common scenario: one label with multiple possible
			// meanings.
			else {
				List<SynsetScoreEntry> rankedSynsetCandidates = rankSynsetCandidatesBySemantics(inputClass, ontology, synsets);
				ISynsetID topCandidateSynset = rankedSynsetCandidates.get(0).sid;
				return topCandidateSynset;
			}
		}
		else {
			// This is the worst case scenario: there is no appropriate label, and one
			// could not be generated from the local URI. Bail out and go have a scotch.
			return null;
		}
	}
	
	// Transforms the local part of the URI into a string to be used to find a synset 
	private static String getLabelFromUri(OWLClass inputClass, OWLOntology ontology) {
		String globalURI =  inputClass.getIRI().toString();
		//System.out.println("Global: " + globalURI);
		String ontologyURI = ontology.getOntologyID().getOntologyIRI().toString();
		//System.out.println("Ontology: " + ontologyURI);

		String localURI;
		// If global class uri is an extension of the ontology uri (which it should be in a
		// well-formed ontology), calculate local uri by removing the ontology uri from the global
		// class uri
		if (globalURI.indexOf(ontologyURI) != -1) {
			localURI = globalURI.substring(globalURI.indexOf(ontologyURI) + ontologyURI.length());
		}
		// Otherwise, just take what is left after last hash sign (ugly)
		else {
			localURI = globalURI.substring(globalURI.lastIndexOf("#") + 1);
		}
		//System.out.println("Local: " + localURI);
		return localURI;
	}
	
	// Should normalize terms (i.e. remove underscore case, camelcase, possibly stem, etc)
	// Also strip junk characters such as hash
	private static String getNormalizedWord(String inputWord) {
		//System.out.println("Normalizing: " + inputWord);
		String wordToNormalize = inputWord.replace("#", "");
		wordToNormalize = wordToNormalize.replace("-", "");
		wordToNormalize = wordToNormalize.replace(",", "");
		wordToNormalize = wordToNormalize.replace(".", "");
		wordToNormalize = wordToNormalize.replace(")", "");
		wordToNormalize = wordToNormalize.replace("(", "");

		// If stripping of junk characters have eradicated the string entirely, return an empty string
		// (which will be caught by the stop word function or other code?)
		if (wordToNormalize.length() < 1) {
			return wordToNormalize;
		}
		else {
			for (CaseFormat c : CaseFormat.values()) {
				wordToNormalize = c.to(CaseFormat.LOWER_UNDERSCORE, wordToNormalize);
			}
			
			List<String> suggestedStems = stemmer.findStems(wordToNormalize, POS.NOUN);
			if (suggestedStems.size() > 0) {
				String suggestedStem = suggestedStems.get(0);
				System.out.println("Stemmed: " + suggestedStem);
				return suggestedStem;
			} 
			else {
				System.out.println("Normalized: " + wordToNormalize);
				return wordToNormalize;
			}
		}
	}
	
	// Returns false for words which exist as nouns in English WordNet and which are NOT stop words.
	// Also returns false for strings which when stripped are empty.
	private static Boolean isStopOrNonexistantWord(String inputWord) {
		if (inputWord.replace(" ", "").length() < 1) {
			return true;
		}
		if (stopwords.contains(inputWord)) {
			return true;
		}
		if(dict.getIndexWord(inputWord, POS.NOUN) == null) {
			return true;
		}
		// We're in the clear!
		return false;
	}
	
	// Find the most common synset id associated with the label set.
	private static ISynsetID getMostCommonSynset(Set<String> labels) {
		Map<ISynsetID,Integer> SynsetIdOccurences = new HashMap<ISynsetID,Integer>();
		// For each label, find the synsets ids and add them to the occurences map
		for (String label: labels) {
			Set<ISynsetID> candidateSynsetIds = getAllSynsetsForLabel(label);
			for (ISynsetID candidateSynsetId: candidateSynsetIds) {
				if (SynsetIdOccurences.containsKey(candidateSynsetId)) {
					SynsetIdOccurences.put(candidateSynsetId, SynsetIdOccurences.get(candidateSynsetId) + 1);
				}
				else {
					SynsetIdOccurences.put(candidateSynsetId, 0);
				}
			}
		}
		// Iterate over the occurences map once to pick out the top occuring synset id
		ISynsetID topSynsetId = null;
		Integer topOccurenceCount = 0;
		for (Map.Entry<ISynsetID, Integer> entry : SynsetIdOccurences.entrySet()) {
			if (entry.getValue() > topOccurenceCount) {
				topSynsetId = entry.getKey();
			}
		}
		return topSynsetId;
	}
	
	// Returns all synset IDs associated with a particular term/lemma
	private static Set<ISynsetID> getAllSynsetsForLabel(String label) {
		Set<ISynsetID> synsetIds = new HashSet<ISynsetID>();
		IIndexWord idxWord = dict.getIndexWord(label, POS.NOUN);
		List<IWordID> wordIds = idxWord.getWordIDs();
		for (IWordID wordId: wordIds) {
			synsetIds.add(dict.getWord(wordId).getSynset().getID());
		}
		return synsetIds;
	}
	
	// Using the semantics of the class in question, and the hypernymy/hyponymy each candidate synset,
	// rank them from most likely match to least likely match.
	private static List<SynsetScoreEntry> rankSynsetCandidatesBySemantics(OWLClass inputClass, OWLOntology ontology, Set<ISynsetID> synsets) {
		List<SynsetScoreEntry> confidenceValueList = new ArrayList<SynsetScoreEntry>();

		// Initially populate confidenceValueList from only labels and comments of class
		// Do this by finding which synset has the most of its lemmas in the comment and
		// label annotations of the class. 
		Set<OWLAnnotation> inputClassAnnotations = inputClass.getAnnotations(ontology);
		Set<String> inputClassLabelsAndCommentStrings = new HashSet<String>();
		
		
		// Get all labels and comments that are in english or lack language definition
		// Stop words are removed and other words are normalized using the private
		// method for that purpose. Strings are split on space.
		for (OWLAnnotation anAnnotation: inputClassAnnotations) {
			if (anAnnotation.getProperty().isComment() || anAnnotation.getProperty().isLabel()) {
				if (anAnnotation.getValue() instanceof OWLLiteral) {
					OWLLiteral anAnnotationAsLiteral = (OWLLiteral)anAnnotation.getValue();
					String annotationLang = anAnnotationAsLiteral.getLang();
					if (annotationLang == "" || annotationLang == "en") {
						String annotationString = anAnnotationAsLiteral.getLiteral();
						for (String annotationWord: annotationString.split(" ")) {
							String normalizedWord = getNormalizedWord(annotationWord);
							if (!isStopOrNonexistantWord(normalizedWord)) {
								inputClassLabelsAndCommentStrings.add(normalizedWord);
							}
						}
					}
				}
			}
		}
		
		for (ISynsetID sidCandidate: synsets) {
			// Calculate confidence value in terms of how many synset lemmas 
			// that are found in class label and comments
			ISynset synsetCandidate = dict.getSynset(sidCandidate);
			List<IWord> words = synsetCandidate.getWords();
			Double totalLemmaCount = new Double(words.size());
			Double matchingLemmas = 0.0;
			for (IWord word: words) {
				if (inputClassLabelsAndCommentStrings.contains(word.getLemma())) {
					matchingLemmas++;
				}
			}
			Double matchingScore = matchingLemmas / totalLemmaCount;
			SynsetScoreEntry scoreEntry = new SynsetScoreEntry(sidCandidate, matchingScore);
			confidenceValueList.add(scoreEntry);
		}
		
		// Get super and subclasses for further evaluation
		Set<OWLClassExpression> subClasses= inputClass.getSubClasses(ontology);
		Set<OWLClassExpression> superClasses= inputClass.getSuperClasses(ontology);

		if (subClasses.size() == 0 && superClasses.size() != 0) {
			// Find synset candidates from superclass structure
			List<SynsetScoreEntry> hypernymMatchingConfidenceScores = getConfidenceScoreListByNymy(inputClass, ontology, synsets, "hyper");
			confidenceValueList = mergeConfidenceScoreLists(confidenceValueList, hypernymMatchingConfidenceScores);
		}
		if (subClasses.size() != 0 && superClasses.size() == 0) {
			// Find synset candidates from subclass structure
			List<SynsetScoreEntry> hyponymMatchingConfidenceScores = getConfidenceScoreListByNymy(inputClass, ontology, synsets, "hypo");
			confidenceValueList = mergeConfidenceScoreLists(confidenceValueList, hyponymMatchingConfidenceScores);
		}
		if (subClasses.size() != 0 && superClasses.size() != 0) {
			// Find synset candidates from both super and subclass structures
			List<SynsetScoreEntry> hypernymMatchingConfidenceScores = getConfidenceScoreListByNymy(inputClass, ontology, synsets, "hyper");
			List<SynsetScoreEntry> hyponymMatchingConfidenceScores = getConfidenceScoreListByNymy(inputClass, ontology, synsets, "hypo");
			confidenceValueList = mergeConfidenceScoreLists(confidenceValueList, hypernymMatchingConfidenceScores);
			confidenceValueList = mergeConfidenceScoreLists(confidenceValueList, hyponymMatchingConfidenceScores);
		}
		
		// Sort the merged list by scores, then reverse to get highest-scoring first
		Collections.sort(confidenceValueList, new SynsetScoreEntryComparator());
		Collections.reverse(confidenceValueList);
		return confidenceValueList;
	}
	
	// Compute confidence score list for how well the different input synsets match to the input class, 
	// based on hypernymy or hyponymy relations, that is, how well lemmas of sub or superconcepts in Wordnet
	// match to labels and comments of sub or superclasses of the input class in question. The direction string 
	// parameter is either "hyper" or "hypo", denoting which direction we are searching for matches.
	// Note that this is an aggregate metric, such that it simply measures the degree to which ALL hypernyms
	// match ALL superclasses - no more fine grained matching is performed!
	private static List<SynsetScoreEntry> getConfidenceScoreListByNymy (OWLClass inputClass, OWLOntology ontology, Set<ISynsetID> synsets, String direction) {
		List<SynsetScoreEntry> confidenceValueList = new ArrayList<SynsetScoreEntry>();
		
		// Set up variables (sub or superclasses to compare against, set of related description terms
		// to compute synset matching confidence, etc)
		Set<String> relatedDescriptionStrings = new HashSet<String>();
		Set<OWLClassExpression> classesToCompareAgainst;
		if (direction.equals("hyper")) {
			classesToCompareAgainst = inputClass.getSuperClasses(ontology);
		}
		else {
			classesToCompareAgainst = inputClass.getSubClasses(ontology);
		}
		
		// Get out all labels and comments of asserted sub or superclasses
		// (which are not anonymous, OWL:Thing, or OWL:Nothing)
		// TODO: Remove comments and see how that works out?
		for (OWLClassExpression oce: classesToCompareAgainst) {
			if (!oce.isAnonymous() && !oce.isOWLThing() && !oce.isOWLNothing()) {
				OWLClass aClass = oce.asOWLClass();
				Set<OWLAnnotation> aClassAnnotations = aClass.getAnnotations(ontology);
				for (OWLAnnotation anAnnotation: aClassAnnotations) {
					if (anAnnotation.getProperty().isComment() || anAnnotation.getProperty().isLabel()) {
						if (anAnnotation.getValue() instanceof OWLLiteral) {
							OWLLiteral anAnnotationAsLiteral = (OWLLiteral)anAnnotation.getValue();
							String annotationLang = anAnnotationAsLiteral.getLang();
							if (annotationLang == "" || annotationLang == "en") {
								String annotationString = anAnnotationAsLiteral.getLiteral();
								for (String annotationWord: annotationString.split(" ")) {
									String normalizedWord = getNormalizedWord(annotationWord);
									if (!isStopOrNonexistantWord(normalizedWord)) {
										relatedDescriptionStrings.add(normalizedWord);
									}
								}
							}
						}
					}
				}
			}
		}
		
		// TODO: Consider reversing matching methods: here we see the ontology terms cover synset lemmas, 
		// possibly the reverse (how well synset lemma convers ontology terms) is also relevant. On the other
		// hand, ontology terms are messy and unstructured if RDFS comments are included.
		
		// Iterate over synset candidates to find overlap with the above calculated descriptions
		for (ISynsetID sidCandidate: synsets) {
			Double matchingLemmas = 0.0;
			// TODO: Vary the depth indicator to see how that works out
			Set<String> relatedLemmas = getRelatedLemmas(sidCandidate, 2, direction);
			Double totalLemmaCount = new Double(relatedLemmas.size());
			for (String lemma: relatedLemmas) {
				if (relatedDescriptionStrings.contains(lemma)) {
					matchingLemmas++;
				}
			}
			Double matchingScore = matchingLemmas / totalLemmaCount;
			SynsetScoreEntry scoreEntry = new SynsetScoreEntry(sidCandidate, matchingScore);
			confidenceValueList.add(scoreEntry);
		}
		return confidenceValueList;
	}
	
	// TODO: Make this function recursive, allowing depth to be arbitrarily deep, not only two steps
	private static Set<String> getRelatedLemmas(ISynsetID sid, Integer depth, String direction) {
		Set<String> relatedLemmas = new HashSet<String>();
		
		Pointer directionPointer = null;
		if (direction == "hyper") {
			directionPointer = Pointer.HYPERNYM;
		}
		else {
			directionPointer = Pointer.HYPONYM;
		}
		
		if (depth == 1) {
			List<ISynsetID> parentSyns = dict.getSynset(sid).getRelatedSynsets(directionPointer);
			for (ISynsetID parentSynId: parentSyns) {
				List<IWord> parentWords = dict.getSynset(parentSynId).getWords();
				for (IWord parentWord: parentWords) {
					relatedLemmas.add(parentWord.getLemma());
				}
			}
		}
		if (depth >= 2) {
			List<ISynsetID> parentSyns = dict.getSynset(sid).getRelatedSynsets(directionPointer);
			for (ISynsetID parentSynId: parentSyns) {
				ISynset parentSynset = dict.getSynset(parentSynId);
				// Get first level parent words
				List<IWord> parentWords = parentSynset.getWords();
				for (IWord parentWord: parentWords) {
					relatedLemmas.add(parentWord.getLemma());
				}
				List<ISynsetID> SecondParentSynIds = parentSynset.getRelatedSynsets(directionPointer);
				for (ISynsetID SecondParentSyndId: SecondParentSynIds) {
					ISynset SecondParentSynset = dict.getSynset(SecondParentSyndId);
					List<IWord> secondParentWords = SecondParentSynset.getWords();
					for (IWord secondParentWord: secondParentWords) {
						relatedLemmas.add(secondParentWord.getLemma());
					}
				}
			}
		}
		
		/*Set<ISynsetID> unvisitedSynsets = new HashSet<ISynsetID>();
		for (int i = 0; i<depth; i++) {
			// TODO :Finish this
		}*/
		
		return relatedLemmas;

	}
	
	private static List<SynsetScoreEntry> mergeConfidenceScoreLists(List<SynsetScoreEntry> inputList1, List<SynsetScoreEntry> inputList2) {
		// First concatenate the input result lists
		List<SynsetScoreEntry> concatenatedResultsList = new ArrayList<SynsetScoreEntry>();
		concatenatedResultsList.addAll(inputList1);
		concatenatedResultsList.addAll(inputList2);
		
		// Put into a map to merge on keys (ISynsetIDs)
		Map<ISynsetID,Double> mergedResultsMap = new HashMap<ISynsetID,Double>();
		for (SynsetScoreEntry entry: concatenatedResultsList) {
			if (!mergedResultsMap.containsKey(entry.sid)) {
				mergedResultsMap.put(entry.sid, entry.sidScore);
			}
			else {
				Double updatedScore = mergedResultsMap.get(entry.sid) + entry.sidScore;
				mergedResultsMap.put(entry.sid, updatedScore);
			}
		}
		
		// Turn said Map back into a list of SynsetScoreEntry:s
		List<SynsetScoreEntry> mergedResultsList = new ArrayList<SynsetScoreEntry>();
		for (Map.Entry<ISynsetID, Double> entry : mergedResultsMap.entrySet()) {
		    ISynsetID sid = entry.getKey();
		    Double score = entry.getValue();
		    SynsetScoreEntry newEntry = new SynsetScoreEntry(sid,score);
		    mergedResultsList.add(newEntry);
		}
		
		// Return
		return mergedResultsList;
	}
}

// Private class used to structure candidate synsets
class SynsetScoreEntry {
	public ISynsetID sid;
	public Double sidScore;
	
	public SynsetScoreEntry(ISynsetID sid, Double sidScore) {
		this.sid = sid;
		this.sidScore = sidScore;
	}
}

// Private comparator used to order candidate synset scores
class SynsetScoreEntryComparator implements Comparator<SynsetScoreEntry> {
	@Override
	public int compare(SynsetScoreEntry s1, SynsetScoreEntry s2) {
		return s1.sidScore.compareTo(s2.sidScore);
	}
}
