package com.karlhammar.xdpservices.deprecated.index;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

public class IndexController {

    public String rebuildIndex() {
    	return Indexer.INSTANCE.buildIndex();
    }
}
