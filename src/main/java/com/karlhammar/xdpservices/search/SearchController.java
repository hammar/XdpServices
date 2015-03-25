package com.karlhammar.xdpservices.search;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.stanford.bmir.protege.web.shared.xd.OdpDetails;
import edu.stanford.bmir.protege.web.shared.xd.OdpSearchFilterConfiguration;
import edu.stanford.bmir.protege.web.shared.xd.OdpSearchResult;

@RestController
public class SearchController {

    @RequestMapping("/odpSearch")
    public OdpSearchResult[] odpSearch(@RequestParam(value="queryString", required=true) String queryString, 
    		@RequestBody(required=false) OdpSearchFilterConfiguration filterConfiguration) {
    	// Simply defer execution to CompositeSearch
    	return CompositeSearch.INSTANCE.runSearch(queryString,filterConfiguration);
    }
    
    @RequestMapping("/odpsByCategory")
    public OdpDetails[] odpsByCategory(@RequestParam(value="category", required=true) String category) {
    	// Simply defer execution to CompositeSearch
    	return DataFetcher.INSTANCE.getOdpsByCategory(category);
    }
    
    @RequestMapping("/odpDetails")
    public OdpDetails odpDetails(@RequestParam(value="uri", required=true)String uri) {
    	return DataFetcher.INSTANCE.getOdpDetails(uri);
    }
    
    @RequestMapping("/rebuildIndex")
    public String rebuildIndex() {
    	// TODO: Implement index rebuild logic here.
    	return Indexer.INSTANCE.buildIndex();
    }
}