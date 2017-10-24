/* Copyright 2015-2017 Fabian Steeg, hbz. Licensed under the GPLv2 */
package views;

import controllers.nwbib.Application;
import play.Logger;
import play.cache.Cache;
import play.libs.F.Promise;
import play.libs.ws.WS;
import play.libs.ws.WSRequest;
import play.libs.ws.WSRequestHolder;

/**
 * Reverse lookup of a given geolocation to get a label for it.
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class ReverseGeoLookup {

	private static final int TIMEOUT = 1000;
	private String labelLookupURl;
	private String idLookupUrl;
	private int timeout;

	/**
	 * @param location A geolocation formatted as latitude,longitude, e.g.
	 *          "51.433333391323686,7.800000105053186"
	 * @return A label for the given location, e.g. "Menden (Sauerland)"
	 */
	public static String of(String location) {
		return new ReverseGeoLookup(
				"https://wdq.wmflabs.org/api?q=around[625,%s,0.1]",
				"https://www.wikidata.org/w/api.php?action=wbgetentities&props=labels&ids=Q%s&languages=de&format=json",
				TIMEOUT).lookup(location);
	}

	private ReverseGeoLookup(String idLookupUrl, String labelLookupURl,
			int timeout) {
		this.idLookupUrl = idLookupUrl;
		this.labelLookupURl = labelLookupURl;
		this.timeout = timeout;
	}

	private String lookup(String location) {
		Object cached = Cache.get(location);
		if (cached != null) {
			Logger.debug("Using location label from cache for: " + location);
			return (String) cached;
		}
		try {
			WSRequest idRequest = WS.url(String.format(idLookupUrl, location));
			Promise<String> promise = idRequest.get().flatMap(idResponse -> {
				WSRequest labelRequest = WS.url(String.format(labelLookupURl,
						idResponse.asJson().get("items").elements().next().asInt()));
				return labelRequest.get().map(labelResponse -> labelResponse.asJson()
						.findValue("value").asText());
			});
			promise
					.onRedeem(label -> Cache.set(location, label, Application.ONE_DAY));
			return promise.get(timeout);
		} catch (Throwable t) {
			Logger.error("Could not look up location", t);
			return "1 Ort";
		}
	}
}
