package com.karlhammar.xdpservices.deprecated.search;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karlhammar.xdpservices.data.OdpSearchFilterConfiguration;
import com.karlhammar.xdpservices.data.OdpSearchResult;

@RestController
public class SearchController {

    @RequestMapping("/search/odpSearch")
    public OdpSearchResult[] odpSearch(@RequestParam(value="queryString", required=true) String queryString, 
    		@RequestBody(required=false) OdpSearchFilterConfiguration filterConfiguration) {
    	return CompositeSearch.INSTANCE.runSearch(queryString,filterConfiguration);
    }
}