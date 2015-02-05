package com.karlhammar.xdpservices.search;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.bmir.protege.web.shared.xd.OdpDetails;

public class Indexer {

	// Singleton instance.
	public final static Indexer INSTANCE = new Indexer();

	// Singleton properties.
	private static Log log;
	private static Properties searchProperties;
	
	/**
	 * Private singleton constructor setting up all the statics that are needed. 
	 */
	private Indexer() {
		log = LogFactory.getLog(Indexer.class);
		loadSearchProperties();
		//stopwords = loadStopWords();
		//loadWordNetDictionary();
		//loadLuceneReader();
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
	 * Builds Lucene index from the contents of the ODP repository. This wrapper method just 
	 * finds the repository directory and iterates over the files therein; the real indexing work
	 * is done in the indexODP() method. 
	 * @return A user friendly indexing success/failure message string.
	 */
	public String buildIndex() {
		String odpRepositoryPath = searchProperties.getProperty("odpRepositoryPath");
		File odpRepository = new File(odpRepositoryPath);
		if (!odpRepository.isDirectory()) {
			log.fatal(String.format("Configured ODP repository path is not a directory: %s", odpRepositoryPath));
			return "Index rebuild failed.";
		}
		else {
			long startTime = System.nanoTime();
			String[] files = odpRepository.list();
			for (int i = 0; i < files.length; i++) {
				OdpDetails odp = parseOdp(new File(odpRepository, files[i]));
				indexOdp(odp);
			}
			long endTime = System.nanoTime();
			float duration = (endTime - startTime)/1000000000;
			return String.format("Index rebuilt in %s seconds.", duration);
		}
	}

	/**
	 * Performs Lucene indexing (using shared static index writer) of a given ODP.
	 * @param odpFile File to add to index.
	 */
	private static void indexOdp(OdpDetails odp) {
		// TODO: Implement this.
		log.info(String.format("Indexing ODP: %s", odp.getUri().toString()));
	}
	
	
	private static OdpDetails parseOdp(File odpFile) {
		// TODO: Implement this.
		log.info(String.format("Parsing ODP: %s", odpFile.toString()));
		return new OdpDetails("http://test.test/");
	}
	
}
