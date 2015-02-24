package com.karlhammar.xdpservices.search;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    
    @RequestMapping("/rebuildIndex")
    public String rebuildIndex() {
    	// TODO: Implement index rebuild logic here.
    	return Indexer.INSTANCE.buildIndex();
    }
}