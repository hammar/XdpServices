package com.karlhammar.xdpservices.index;

import java.io.IOException;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IndexController {
    @RequestMapping("/index/rebuildIndex")
    public String rebuildIndex() throws IOException {
    	return Indexer.INSTANCE.buildIndex();
    }
}
