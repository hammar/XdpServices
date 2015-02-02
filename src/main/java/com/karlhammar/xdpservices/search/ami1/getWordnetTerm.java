package com.karlhammar.xdpservices.search.ami1;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.SynsetID;

public class getWordnetTerm {

	public static void main(String[] args) throws IOException {
		
		// Prepare WordNet
		String WnDictPath = System.getProperty("user.dir") + File.separator + "wordnet" + File.separator + "dict";
		URL url = new URL("file", null, WnDictPath);
		IDictionary wordnetDictionary = new Dictionary(url);
		wordnetDictionary.open();
		
		//Console console = System.console();
		//String input = console.readLine("Enter input:");
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Enter input (q to exit):");
		String input = reader.readLine();
		while (!input.equalsIgnoreCase("q")) {
			
			ISynsetID sid = SynsetID.parseSynsetID(input);
			System.out.println(wordnetDictionary.getSynset(sid).getWord(1).getLemma());
			System.out.println("Enter input (q to exit):");
			input = reader.readLine();
		}
		wordnetDictionary.close();
	}
}
