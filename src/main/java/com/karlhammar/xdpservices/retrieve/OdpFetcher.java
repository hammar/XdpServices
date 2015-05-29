package com.karlhammar.xdpservices.retrieve;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;
import org.coode.owlapi.turtle.TurtleOntologyFormat;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyFormat;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import com.karlhammar.xdpservices.search.CompositeSearch;

public class OdpFetcher {
	
	// Singleton instance.
		public final static OdpFetcher INSTANCE = new OdpFetcher();
		
		private static Log log;
		private static Properties searchProperties;
		private static IndexReader luceneReader;
		private static IndexSearcher luceneSearcher;
	
		private OdpFetcher() {
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
		
		public static String getOdpBuildingBlockTurtle(String odpIri) throws OWLOntologyCreationException, OWLOntologyStorageException, ParseException, IOException {
			log.info(String.format("%s body requested",odpIri));
			// Configure search
			Analyzer analyzer = new WhitespaceAnalyzer();
			QueryParser queryParser = new QueryParser("uri", analyzer);
			Query query = queryParser.parse(String.format("\"%s\"", odpIri));
			
			// Execute search
			ScoreDoc[] hits = luceneSearcher.search(query, null, 1).scoreDocs;
			Document hit = luceneSearcher.doc(hits[0].doc);
			
			// Return path on disk from search result
			IndexableField nameField = hit.getField("path");
			String odpPath = nameField.stringValue();
			File odpFile = new File(odpPath);
			
			// Load ODP. Format is guessed automatically.
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
	        OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
	        FileDocumentSource fds = new FileDocumentSource(odpFile);
	        config = config.setFollowRedirects(false);
	        config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
	        OWLOntology odp = manager.loadOntologyFromOntologyDocument(fds, config);
	        
	        // Set up output format. Copy prefixes from existing file if needed.
	        OWLOntologyFormat format = manager.getOntologyFormat(odp);
	        TurtleOntologyFormat turtleFormat = new TurtleOntologyFormat();
	        if (format.isPrefixOWLOntologyFormat()) {
	        	turtleFormat.copyPrefixesFrom(format.asPrefixOWLOntologyFormat());
	        }
	        
	        // Save ontology into Turtle format into an output stream, send to client
	        ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        manager.saveOntology(odp, turtleFormat, baos);
	        return baos.toString();
		}
}
