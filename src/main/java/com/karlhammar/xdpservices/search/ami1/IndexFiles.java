package com.karlhammar.xdpservices.search.ami1;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import com.google.common.base.CaseFormat;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class IndexFiles {
  
  private IndexFiles() {}

  private static IDictionary wordnetDictionary;
  private static Set<String> stopwords;
  
  /** Index all text files under a directory. 
 * @throws OWLException 
 * @throws IOException */
  public static void main(String[] args) throws OWLException, IOException {
	  
	// prepare stop words  
	FileInputStream fis = new FileInputStream("datasets/stopwords.txt");
	BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	stopwords = new HashSet<String>();
	String line = null;
	while ((line = br.readLine()) != null) {
		stopwords.add(line);
	}
	br.close();
		
	// Prepare WordNet
	String WnDictPath = System.getProperty("user.dir") + File.separator + "wordnet" + File.separator + "dict";
	URL url = new URL("file", null, WnDictPath);
	wordnetDictionary = new Dictionary(url);
	wordnetDictionary.open();
	  
    String usage = "java org.apache.lucene.demo.IndexFiles"
                 + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                 + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                 + "in INDEX_PATH that can be searched with SearchFiles";
    String indexPath = "index";
    String docsPath = null;
    boolean create = true;
    for(int i=0;i<args.length;i++) {
      if ("-index".equals(args[i])) {
        indexPath = args[i+1];
        i++;
      } else if ("-docs".equals(args[i])) {
        docsPath = args[i+1];
        i++;
      } else if ("-update".equals(args[i])) {
        create = false;
      }
    }

    if (docsPath == null) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }

    final File docDir = new File(docsPath);
    if (!docDir.exists() || !docDir.canRead()) {
      System.out.println("Document directory '" +docDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");
      System.exit(1);
    }
    
    Date start = new Date();
    try {
      System.out.println("Indexing to directory '" + indexPath + "'...");

      Directory dir = FSDirectory.open(new File(indexPath));
      // :Post-Release-Update-Version.LUCENE_XY:
      Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_46);
      IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_46, analyzer);

      if (create) {
        // Create a new index in the directory, removing any
        // previously indexed documents:
        iwc.setOpenMode(OpenMode.CREATE);
      } else {
        // Add new documents to an existing index:
        iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
      }

      // Optional: for better indexing performance, if you
      // are indexing many documents, increase the RAM
      // buffer.  But if you do this, increase the max heap
      // size to the JVM (eg add -Xmx512m or -Xmx1g):
      //
      // iwc.setRAMBufferSizeMB(256.0);

      IndexWriter writer = new IndexWriter(dir, iwc);
      indexDocs(writer, docDir);

      // NOTE: if you want to maximize search performance,
      // you can optionally call forceMerge here.  This can be
      // a terribly costly operation, so generally it's only
      // worth it when your index is relatively static (ie
      // you're done adding documents to it):
      //
      // writer.forceMerge(1);

      writer.close();

      Date end = new Date();
      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
       "\n with message: " + e.getMessage());
    }
  }

  /**
   * Indexes the given file using the given writer, or if a directory is given,
   * recurses over files and directories found under the given directory.
   * 
   * NOTE: This method indexes one document per input file.  This is slow.  For good
   * throughput, put multiple documents into your input file(s).  An example of this is
   * in the benchmark module, which can create "line doc" files, one document per line,
   * using the
   * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
   * >WriteLineDocTask</a>.
   *  
   * @param writer Writer to the index where the given file/dir info will be stored
   * @param file The file to index, or the directory to recurse into to find files to index
 * @param wordnetDictionary 
   * @throws IOException If there is a low-level I/O error
 * @throws OWLException 
   */
  static void indexDocs(IndexWriter writer, File file)
    throws IOException, OWLException {
    // do not try to index files that cannot be read
    if (file.canRead()) {
      if (file.isDirectory()) {
        String[] files = file.list();
        // an IO error could occur
        if (files != null) {
          for (int i = 0; i < files.length; i++) {
            indexDocs(writer, new File(file, files[i]));
          }
        }
      } else {

        FileInputStream fis;
        try {
          fis = new FileInputStream(file);
        } catch (FileNotFoundException fnfe) {
          // at least on windows, some temporary files raise this exception with an "access denied" message
          // checking if the file can be read doesn't help
          return;
        }

        try {

          // make a new, empty document
          Document doc = new Document();

          // Add the path of the file as a field named "path".  Use a
          // field that is indexed (i.e. searchable), but don't tokenize 
          // the field into separate words and don't index term frequency
          // or positional information:
          Field pathField = new StringField("path", file.getPath(), Field.Store.YES);
          doc.add(pathField);

          OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
          FileDocumentSource fds = new FileDocumentSource(file);
          config = config.setFollowRedirects(false);
          config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
          String odpContents = "";
          OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
          OWLOntology odp = manager.loadOntologyFromOntologyDocument(fds, config);
          OWLDataFactory df = manager.getOWLDataFactory();
          
		  // TODO: Remove this variable once SID matching is done via own field
		  //String synonyms = "";
          
          // Index URI of ODP
          IRI odpIRI = odp.getOntologyID().getOntologyIRI();
    	  // We only index ODPs that have HTTP URI:s, damn it!
          if (odpIRI == null) {
        	  return;
          } 
          else if (!odpIRI.toString().startsWith("http://")) {
        	  return;
          }
          else {
        	String odpUri = odpIRI.toString().toLowerCase();
          	Field uriField = new StringField("uri", odpUri, Field.Store.YES);
          	doc.add(uriField);
          }
          
          // Process entities in the ODP to generate the relevant indexes.
          Set<OWLEntity> entities = odp.getSignature();
          for (OWLEntity e: entities) {
    		  // Process entity URI
    		  String globalURI =  e.getIRI().toString();
    		  String localURI = globalURI.substring(globalURI.lastIndexOf("/") + 1);
    		  for (CaseFormat c : CaseFormat.values())
    			  localURI = c.to(CaseFormat.LOWER_UNDERSCORE, localURI);
    		  String processedLocalURI = localURI.replace("_", " ").replace("-", " "); 
    		  odpContents += (" " + processedLocalURI);
    		  

    		  // Process OWL class specific details 
    		  if (e.isOWLClass()) {
    			  // Get synset id for class and add to new synsetIds index
    			  // for later resolving against enriched query hypernyms at runtime.
    			  ISynsetID sid = WordnetSynsetMatcher.suggestWordnetSynsetId(e.asOWLClass(), odp, wordnetDictionary, stopwords);
    			  if (sid != null) {
    				  Field sidField = new StringField("sid", sid.toString(), Field.Store.YES);
                      doc.add(sidField);
    			  }
    		  }
    		  
    		  // Add all label and comment annotations
    		  Set<OWLAnnotation> annotations = e.getAnnotations(odp);
        	  for (OWLAnnotation a: annotations) {
        		  if (a.getProperty().isLabel() || a.getProperty().isComment()) {
        			  if (a.getValue() instanceof OWLLiteral){
	        			  OWLLiteral annotationAsLiteral = (OWLLiteral)a.getValue();
	        			  String annotationLang = annotationAsLiteral.getLang();
	        			  if (annotationLang == "en" || annotationLang == "EN" || annotationLang == "") { 
	        				  odpContents += (" " + annotationAsLiteral.getLiteral());
	        			  }
        			  }
        		  }
        	  }
          }
          doc.add(new TextField("contents", odpContents, Field.Store.NO));
          
          // Split up ODP contents into words, and remove too short ones
          String[] odpWords = odpContents.split(" ");
          List<String> listOfOdpWords = new ArrayList<String>();
          for (String odpWord: odpWords) {
        	  if (odpWord.length()>2) {
        		  listOfOdpWords.add(odpWord);
        	  }
          }
          
          // Find synonyms for each ODP word and add to index as one space-delimited string of
          // synonym terms.
          String synonyms = "";
          for (String odpWord: listOfOdpWords) {
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

          // Get ODP CQ:s and add to CQ index.
          OWLAnnotationProperty coversRequirements = df.getOWLAnnotationProperty(
        		  IRI.create("http://www.ontologydesignpatterns.org/schemas/cpannotationschema.owl#coversRequirements"));
          Set<OWLAnnotation> annotations = odp.getAnnotations();
          for (OWLAnnotation annotation: annotations) {
        	  if (annotation.getProperty().equals(coversRequirements)) {
        		  if (annotation.getValue() instanceof OWLLiteral) {
                      OWLLiteral val = (OWLLiteral) annotation.getValue();
                      // Split val on question mark (sometimes multiple CQ:s are referred by a 
                      // single annotation statement) and trim result, then adding back question mark, 
                      // before indexing in CQ field.
                      String[] cqs = val.getLiteral().split("\\?");
                      for (String cq: cqs) {
                    	  cq = cq.trim() + "?";
                    	  if (cq.length() > 0) {
                    		  Field cqField = new StringField("CQ", cq, Field.Store.YES);
                              doc.add(cqField);
                    	  }
                      }
        		  }
        	  }
          }
          
          if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
            // New index, so we just add the document (no old document can be there):
            System.out.println("adding " + file);
            writer.addDocument(doc);
          } else {
            // Existing index (an old copy of this document may have been indexed) so 
            // we use updateDocument instead to replace the old one matching the exact 
            // path, if present:
            System.out.println("updating " + file);
            writer.updateDocument(new Term("path", file.getPath()), doc);
          }
          
        } finally {
          fis.close();
        }
      }
    }
  }
}
