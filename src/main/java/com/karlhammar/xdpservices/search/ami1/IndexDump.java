package com.karlhammar.xdpservices.search.ami1;

import java.io.File;
import java.io.IOException;

//import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
//import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

public class IndexDump {

	public static void main(String[] args) throws IOException {
		IndexReader reader = DirectoryReader.open(FSDirectory.open(new File("index")));
		/* The commented out code below gets only the SIDs for each pattern and was used for figuring out
		 * why SID matching didn't work as well as expected.
		
		for (int i=0; i<reader.maxDoc(); i++) {
			Document doc = reader.document(i);
			IndexableField pathField = doc.getField("path");
			String suggestedOdpPath = pathField.stringValue();
			String suggestedOdpFilename = suggestedOdpPath.substring(suggestedOdpPath.lastIndexOf("/") + 1);
			
			IndexableField[] fields = doc.getFields("sid");
			for (IndexableField field: fields) {
				System.out.println(suggestedOdpFilename + ";" + field.stringValue());
			}
		}*/
		
		Fields fields = MultiFields.getFields(reader);
		if (fields != null) {
			for(String field : fields) {
				Terms terms = fields.terms(field);
				if (terms != null) {
					TermsEnum termsEnum = terms.iterator(null);
					BytesRef text;
					while((text = termsEnum.next()) != null) {
						System.out.println(field + ";" + text.utf8ToString());
					}
				}
			}
		}
	}
}
