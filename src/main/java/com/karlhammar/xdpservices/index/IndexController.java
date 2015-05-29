package com.karlhammar.xdpservices.index;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IndexController {
    @RequestMapping("/index/rebuildIndex")
    public String rebuildIndex() {
    	return Indexer.INSTANCE.buildIndex();
    }
}
