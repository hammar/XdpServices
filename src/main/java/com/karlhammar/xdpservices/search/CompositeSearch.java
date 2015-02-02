package com.karlhammar.xdpservices.search;

import java.util.Arrays;
import java.util.List;

import edu.stanford.bmir.protege.web.shared.xd.OdpDetails;
import edu.stanford.bmir.protege.web.shared.xd.OdpSearchFilterConfiguration;
import edu.stanford.bmir.protege.web.shared.xd.OdpSearchResult;

public class CompositeSearch {

	public final static CompositeSearch INSTANCE = new CompositeSearch();

	// Exists only to defeat instantiation.
	private CompositeSearch() {
	}
	
	// TODO: Remove below junk debug data once real search is implemented.
	private OdpDetails od1 = new OdpDetails("http://www.ontologydesignpatterns.org/cp/owl/naryparticipation.owl",
			"Nary Participation",
			"All sorts of relations denoting events with multiple participants, space-time indexing, etc. can be represented with this pattern. When objects participate at the event at different times or with different parts, more elementary nary-participation instances must be created, and made parts of the main one.",
			"General",
			"What are the participants in that event at this time?, What events had what participants in that location?",
			"http://ontologydesignpatterns.org/wiki/images/e/e2/Naryparticipation.jpg");
	private OdpDetails od2 = new OdpDetails("http://www.ontologydesignpatterns.org/cp/owl/informationrealization.owl",
			"Information Realization",
			"This is a basic patterns, representing the difference between abstract and realized (manifested, concrete, etc.) information.",
			"Semiotics",
			"what are the physical realizations of this information object?, what information objects are realized by this physical object?",
			"http://ontologydesignpatterns.org/wiki/images/7/7b/Informationrealization.jpg");
	private OdpDetails od3 = new OdpDetails("http://infoeng.se/~karl/ilog2014/odps/accountability.owl",
			"Accountability",
			"This pattern captures time-limited relations or responsibilities that people and organisations can have to one another and that allows for certain actions to be taken.",
			"Business modelling",
			"Who can drive the Duff Blimp?,Has Homer signed patient consent for Dr Nick to do a hysterectomy?,How many people does Moe housewatch for in the summer of 2011?",
			"http://www.infoeng.se/~karl/temp/accountability.png");
	
	
	public List<OdpSearchResult> runSearch(String queryString, OdpSearchFilterConfiguration filterConfiguration) {
		// TODO: Actually implement this.
		OdpSearchResult[] hits = {new OdpSearchResult(od1,0.30), new OdpSearchResult(od2,0.60), new OdpSearchResult(od3,0.90)};
		if (filterConfiguration != null) {
			// TODO: Do filtering over result set
		}
		return Arrays.asList(hits);
	}
}